package lk.srilankannews.ingestion.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.ingestion.run.api.ClaimRequest;
import lk.srilankannews.ingestion.run.api.ClaimResponse;
import lk.srilankannews.ingestion.run.api.CompleteRequest;
import lk.srilankannews.ingestion.run.api.FailRequest;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class IngestionRunServiceTest {

    @Mock
    private IngestionRunRepository runRepository;
    @Mock
    private IngestionSourceLeaseRepository leaseRepository;
    @Mock
    private SourceService sourceService;

    private Clock clock;
    private IngestionRunService service;

    private final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        service = new IngestionRunService(runRepository, leaseRepository, sourceService, clock);
    }

    @Test
    void claim_succeedsWhenNoActiveLease() {
        Source source = createSource("source-1", "test-slug", true);
        when(sourceService.findBySlug("test-slug")).thenReturn(Optional.of(source));
        when(leaseRepository.findById("source-1")).thenReturn(Optional.empty());

        when(leaseRepository.acquireLease(eq("source-1"), any(), eq("worker-1"), any(), eq(NOW)))
                .thenAnswer(inv -> Optional.of(new IngestionSourceLease(
                        "source-1", inv.getArgument(1), "worker-1", inv.getArgument(3))));

        ClaimRequest request = new ClaimRequest("test-slug", IngestionTriggerType.SCHEDULED, NOW, "worker-1");
        ClaimResponse response = service.claim(request);

        assertThat(response.claimed()).isTrue();
        assertThat(response.runId()).isNotNull();

        ArgumentCaptor<IngestionRun> runCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        verify(runRepository).save(runCaptor.capture());
        
        IngestionRun saved = runCaptor.getValue();
        assertThat(saved.status()).isEqualTo(IngestionRunStatus.RUNNING);
        assertThat(saved.workerId()).isEqualTo("worker-1");
    }

    @Test
    void claim_savedRunIdMatchesReturnedRunId() {
        // Regression: claim was returning a pre-generated runId but saving the document
        // with id=null, causing Spring to auto-generate a different ObjectId.
        Source source = createSource("source-1", "test-slug", true);
        when(sourceService.findBySlug("test-slug")).thenReturn(Optional.of(source));
        when(leaseRepository.findById("source-1")).thenReturn(Optional.empty());

        when(leaseRepository.acquireLease(eq("source-1"), any(), eq("worker-1"), any(), eq(NOW)))
                .thenAnswer(inv -> Optional.of(new IngestionSourceLease(
                        "source-1", inv.getArgument(1), "worker-1", inv.getArgument(3))));

        ClaimRequest request = new ClaimRequest("test-slug", IngestionTriggerType.SCHEDULED, NOW, "worker-1");
        ClaimResponse response = service.claim(request);

        assertThat(response.claimed()).isTrue();
        String returnedRunId = response.runId();
        assertThat(returnedRunId).isNotNull();

        // The saved document's id MUST equal the runId returned in the API response
        ArgumentCaptor<IngestionRun> runCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        verify(runRepository).save(runCaptor.capture());
        IngestionRun saved = runCaptor.getValue();
        assertThat(saved.id()).isEqualTo(returnedRunId);
    }

    @Test
    void claimThenHeartbeatThenComplete_fullLifecycle() {
        // End-to-end: claim returns runId, then heartbeat + complete both find the same run.
        Source source = createSource("source-1", "test-slug", true);
        when(sourceService.findBySlug("test-slug")).thenReturn(Optional.of(source));
        when(leaseRepository.findById("source-1")).thenReturn(Optional.empty());

        when(leaseRepository.acquireLease(eq("source-1"), any(), eq("worker-1"), any(), eq(NOW)))
                .thenAnswer(inv -> Optional.of(new IngestionSourceLease(
                        "source-1", inv.getArgument(1), "worker-1", inv.getArgument(3))));

        ClaimRequest claimRequest = new ClaimRequest("test-slug", IngestionTriggerType.SCHEDULED, NOW, "worker-1");
        ClaimResponse claimResponse = service.claim(claimRequest);
        String runId = claimResponse.runId();

        // Capture the saved run so we can stub findById with it
        ArgumentCaptor<IngestionRun> saveCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        verify(runRepository).save(saveCaptor.capture());
        IngestionRun savedRun = saveCaptor.getValue();
        assertThat(savedRun.id()).isEqualTo(runId);

        // Stub findById to return the saved run — this is what the real repository does
        when(runRepository.findById(runId)).thenReturn(Optional.of(savedRun));
        when(leaseRepository.extendLease(eq("source-1"), eq(runId), any()))
                .thenReturn(Optional.of(new IngestionSourceLease("source-1", runId, "worker-1", NOW.plusSeconds(600))));

        // Heartbeat should succeed with the same runId
        Instant heartbeatExpiry = service.heartbeat(runId);
        assertThat(heartbeatExpiry).isEqualTo(NOW.plusSeconds(600));

        // Complete should succeed with the same runId
        // Re-stub findById since heartbeat saved a modified version
        ArgumentCaptor<IngestionRun> heartbeatCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        verify(runRepository, org.mockito.Mockito.times(2)).save(heartbeatCaptor.capture());
        IngestionRun afterHeartbeat = heartbeatCaptor.getAllValues().get(1);
        when(runRepository.findById(runId)).thenReturn(Optional.of(afterHeartbeat));

        CompleteRequest completeRequest = new CompleteRequest(10, 8, 7, 1);
        service.complete(runId, completeRequest);

        ArgumentCaptor<IngestionRun> completeCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        verify(runRepository, org.mockito.Mockito.times(3)).save(completeCaptor.capture());
        IngestionRun completed = completeCaptor.getAllValues().get(2);
        assertThat(completed.status()).isEqualTo(IngestionRunStatus.COMPLETED);
        assertThat(completed.id()).isEqualTo(runId);
        assertThat(completed.articlesDiscovered()).isEqualTo(10);
        assertThat(completed.articlesFailed()).isEqualTo(1);

        verify(leaseRepository).releaseLease("source-1", runId);
    }

    @Test
    void claim_interruptsExpiredRun_directLookup() {
        // Normal case: lease.runId matches IngestionRun._id
        Source source = createSource("source-1", "test-slug", true);
        when(sourceService.findBySlug("test-slug")).thenReturn(Optional.of(source));
        
        IngestionSourceLease expiredLease = new IngestionSourceLease("source-1", "old-run", "old-worker", NOW.minusSeconds(10));
        when(leaseRepository.findById("source-1")).thenReturn(Optional.of(expiredLease));
        
        when(leaseRepository.acquireLease(eq("source-1"), any(), eq("worker-1"), any(), eq(NOW)))
                .thenAnswer(inv -> Optional.of(new IngestionSourceLease(
                        "source-1", inv.getArgument(1), "worker-1", inv.getArgument(3))));
                        
        IngestionRun oldRun = createRun("old-run", "source-1", IngestionRunStatus.RUNNING);
        when(runRepository.findById("old-run")).thenReturn(Optional.of(oldRun));

        ClaimRequest request = new ClaimRequest("test-slug", IngestionTriggerType.SCHEDULED, NOW, "worker-1");
        ClaimResponse response = service.claim(request);

        assertThat(response.claimed()).isTrue();

        ArgumentCaptor<IngestionRun> runCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        org.mockito.Mockito.verify(runRepository, org.mockito.Mockito.times(2)).save(runCaptor.capture());
        
        // Expected save order:
        // 1. `runRepository.save(newRun)` (Line 90)
        // 2. `interruptSpecificRun` -> `runRepository.save(interruptedRun)` (Line 110)
        
        assertThat(runCaptor.getAllValues()).hasSize(2);
        
        IngestionRun newRun = runCaptor.getAllValues().get(0);
        assertThat(newRun.status()).isEqualTo(IngestionRunStatus.RUNNING);
        
        IngestionRun interruptedRun = runCaptor.getAllValues().get(1);
        assertThat(interruptedRun.id()).isEqualTo("old-run");
        assertThat(interruptedRun.status()).isEqualTo(IngestionRunStatus.INTERRUPTED);
        
        // Fallback query SHOULD have been called (unconditional now) but should return empty
        verify(runRepository).findBySourceIdAndStatusAndLeaseExpiresAtBefore(eq("source-1"), eq(IngestionRunStatus.RUNNING), eq(NOW));
    }

    @Test
    void claim_interruptsOrphanedLegacyRun_fallbackRecovery() {
        // Legacy case: lease.runId was a pre-generated ID that does NOT match any IngestionRun._id
        // (caused by the Phase 29 ID bug where createRunning passed null for id).
        Source source = createSource("source-1", "test-slug", true);
        when(sourceService.findBySlug("test-slug")).thenReturn(Optional.of(source));

        // Lease has runId="phantom-id" which has no matching IngestionRun
        IngestionSourceLease expiredLease = new IngestionSourceLease("source-1", "phantom-id", "old-worker", NOW.minusSeconds(10));
        when(leaseRepository.findById("source-1")).thenReturn(Optional.of(expiredLease));

        when(leaseRepository.acquireLease(eq("source-1"), any(), eq("worker-1"), any(), eq(NOW)))
                .thenAnswer(inv -> Optional.of(new IngestionSourceLease(
                        "source-1", inv.getArgument(1), "worker-1", inv.getArgument(3))));

        // Direct lookup for "phantom-id" returns empty
        when(runRepository.findById("phantom-id")).thenReturn(Optional.empty());

        // Fallback: there IS an orphaned RUNNING run for this source with expired lease
        IngestionRun orphanedRun = createRunWithExpiry("actual-old-id", "source-1", IngestionRunStatus.RUNNING, NOW.minusSeconds(60));
        when(runRepository.findBySourceIdAndStatusAndLeaseExpiresAtBefore(
                "source-1", IngestionRunStatus.RUNNING, NOW
        )).thenReturn(List.of(orphanedRun));

        ClaimRequest request = new ClaimRequest("test-slug", IngestionTriggerType.SCHEDULED, NOW, "worker-1");
        ClaimResponse response = service.claim(request);

        assertThat(response.claimed()).isTrue();

        ArgumentCaptor<IngestionRun> runCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        org.mockito.Mockito.verify(runRepository, org.mockito.Mockito.times(2)).save(runCaptor.capture());

        // First save = interrupted orphaned run, second save = new run
        IngestionRun interrupted = runCaptor.getAllValues().get(0);
        assertThat(interrupted.id()).isEqualTo("actual-old-id");
        assertThat(interrupted.status()).isEqualTo(IngestionRunStatus.INTERRUPTED);
    }

    @Test
    void claim_interruptsExpiredOrphanRun_whenNoLeaseExists() {
        // Orphan case: no IngestionSourceLease exists, but an old IngestionRun is stuck RUNNING
        Source source = createSource("source-1", "test-slug", true);
        when(sourceService.findBySlug("test-slug")).thenReturn(Optional.of(source));

        // NO lease exists
        when(leaseRepository.findById("source-1")).thenReturn(Optional.empty());

        when(leaseRepository.acquireLease(eq("source-1"), any(), eq("worker-1"), any(), eq(NOW)))
                .thenAnswer(inv -> Optional.of(new IngestionSourceLease(
                        "source-1", inv.getArgument(1), "worker-1", inv.getArgument(3))));

        // Orphaned run exists with expired leaseExpiresAt
        IngestionRun orphanedRun = createRunWithExpiry("orphan-id", "source-1", IngestionRunStatus.RUNNING, NOW.minusSeconds(60));
        when(runRepository.findBySourceIdAndStatusAndLeaseExpiresAtBefore(
                "source-1", IngestionRunStatus.RUNNING, NOW
        )).thenReturn(List.of(orphanedRun));

        ClaimRequest request = new ClaimRequest("test-slug", IngestionTriggerType.SCHEDULED, NOW, "worker-1");
        ClaimResponse response = service.claim(request);

        assertThat(response.claimed()).isTrue();

        ArgumentCaptor<IngestionRun> runCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        org.mockito.Mockito.verify(runRepository, org.mockito.Mockito.times(2)).save(runCaptor.capture());

        // First save = interrupted orphaned run, second save = new run
        IngestionRun interrupted = runCaptor.getAllValues().get(0);
        assertThat(interrupted.id()).isEqualTo("orphan-id");
        assertThat(interrupted.status()).isEqualTo(IngestionRunStatus.INTERRUPTED);
    }

    @Test
    void claim_activeLease_deniesClaimAndDoesNotInterrupt() {
        // Active (non-expired) lease: new claim must be denied, old run stays RUNNING.
        Source source = createSource("source-1", "test-slug", true);
        when(sourceService.findBySlug("test-slug")).thenReturn(Optional.of(source));

        // Lease is NOT expired (expiresAt is in the future)
        IngestionSourceLease activeLease = new IngestionSourceLease("source-1", "active-run", "worker-1", NOW.plusSeconds(300));
        when(leaseRepository.findById("source-1")).thenReturn(Optional.of(activeLease));

        // acquireLease returns empty (no expired or missing lease to claim)
        when(leaseRepository.acquireLease(eq("source-1"), any(), eq("worker-2"), any(), eq(NOW)))
                .thenReturn(Optional.empty());

        ClaimRequest request = new ClaimRequest("test-slug", IngestionTriggerType.SCHEDULED, NOW, "worker-2");
        ClaimResponse response = service.claim(request);

        assertThat(response.claimed()).isFalse();
        assertThat(response.reason()).isEqualTo("ACTIVE_RUN");

        // No run should be saved or interrupted
        verify(runRepository, never()).save(any());
        // The unconditional orphan query IS executed, but should return empty (mocked by default)
        verify(runRepository).findBySourceIdAndStatusAndLeaseExpiresAtBefore(eq("source-1"), eq(IngestionRunStatus.RUNNING), eq(NOW));
    }

    @Test
    void claim_deniedWhenConcurrentAcquisition() {
        Source source = createSource("source-1", "test-slug", true);
        when(sourceService.findBySlug("test-slug")).thenReturn(Optional.of(source));
        when(leaseRepository.findById("source-1")).thenReturn(Optional.empty());

        when(leaseRepository.acquireLease(eq("source-1"), any(), eq("worker-1"), any(), eq(NOW)))
                .thenThrow(new DuplicateKeyException("Concurrent"));

        ClaimRequest request = new ClaimRequest("test-slug", IngestionTriggerType.SCHEDULED, NOW, "worker-1");
        ClaimResponse response = service.claim(request);

        assertThat(response.claimed()).isFalse();
        assertThat(response.reason()).isEqualTo("ACTIVE_RUN");
        verify(runRepository, never()).save(any());
    }

    @Test
    void heartbeat_extendsActiveLease() {
        IngestionRun run = createRun("run-1", "source-1", IngestionRunStatus.RUNNING);
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
        
        when(leaseRepository.extendLease(eq("source-1"), eq("run-1"), any()))
                .thenReturn(Optional.of(new IngestionSourceLease("source-1", "run-1", "worker-1", NOW.plusSeconds(600))));

        Instant newExpiresAt = service.heartbeat("run-1");

        assertThat(newExpiresAt).isEqualTo(NOW.plusSeconds(600));
        ArgumentCaptor<IngestionRun> runCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        verify(runRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().leaseExpiresAt()).isEqualTo(newExpiresAt);
    }

    @Test
    void heartbeat_failsIfLeaseLost() {
        IngestionRun run = createRun("run-1", "source-1", IngestionRunStatus.RUNNING);
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
        
        when(leaseRepository.extendLease(eq("source-1"), eq("run-1"), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.heartbeat("run-1"))
                .isInstanceOf(IngestionRunConflictException.class)
                .hasMessage("Lease is no longer owned by this run");
    }

    @Test
    void complete_succeedsAndReleasesLease() {
        IngestionRun run = createRun("run-1", "source-1", IngestionRunStatus.RUNNING);
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));

        CompleteRequest request = new CompleteRequest(10, 8, 8, 0);
        service.complete("run-1", request);

        ArgumentCaptor<IngestionRun> runCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        verify(runRepository).save(runCaptor.capture());
        
        IngestionRun saved = runCaptor.getValue();
        assertThat(saved.status()).isEqualTo(IngestionRunStatus.COMPLETED);
        assertThat(saved.articlesDiscovered()).isEqualTo(10);
        
        verify(leaseRepository).releaseLease("source-1", "run-1");
    }

    @Test
    void fail_boundsSafeErrorMessageAndReleasesLease() {
        IngestionRun run = createRun("run-1", "source-1", IngestionRunStatus.RUNNING);
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));

        String longError = "A".repeat(500);
        FailRequest request = new FailRequest("FETCH_ERROR", longError, 5, 0, 0, 0);
        service.fail("run-1", request);

        ArgumentCaptor<IngestionRun> runCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        verify(runRepository).save(runCaptor.capture());
        
        IngestionRun saved = runCaptor.getValue();
        assertThat(saved.status()).isEqualTo(IngestionRunStatus.FAILED);
        assertThat(saved.safeErrorMessage()).hasSize(255).endsWith("...");
        
        verify(leaseRepository).releaseLease("source-1", "run-1");
    }

    private Source createSource(String id, String slug, boolean enabled) {
        return new Source(id, "Test", slug, "url", null, null, enabled, NOW, NOW);
    }

    private IngestionRun createRun(String id, String sourceId, IngestionRunStatus status) {
        return new IngestionRun(id, sourceId, "slug", IngestionTriggerType.SCHEDULED, status,
                NOW, NOW, null, "worker-1", NOW.plusSeconds(600), 0, 0, 0, 0, null, null, NOW, NOW);
    }

    private IngestionRun createRunWithExpiry(String id, String sourceId, IngestionRunStatus status, Instant leaseExpiresAt) {
        return new IngestionRun(id, sourceId, "slug", IngestionTriggerType.SCHEDULED, status,
                NOW, NOW, null, "worker-1", leaseExpiresAt, 0, 0, 0, 0, null, null, NOW, NOW);
    }
}
