package lk.srilankannews.ingestion.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionTriggerServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private IngestionTriggerRequestRepository repository;
    @Mock
    private SourceService sourceService;

    private IngestionTriggerService service;

    @BeforeEach
    void setUp() {
        service = new IngestionTriggerService(repository, sourceService, FIXED_CLOCK);
    }

    private static Source testSource() {
        return new Source("src1", "Daily Mirror", "daily-mirror", "https://example.com",
                Language.EN, IngestionType.RSS, true, NOW, NOW);
    }

    // === Manual Trigger Concurrency ===

    @Test
    void requestManualTrigger_newRequest_returnsTrue() {
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(testSource()));

        IngestionTriggerRequest enqueued = new IngestionTriggerRequest(
                "t1", "src1", "daily-mirror", "admin-sub", NOW,
                IngestionTriggerRequest.STATUS_PENDING, 0, null, null, null, null, null
        );
        when(repository.atomicEnqueueTrigger(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(enqueued));

        boolean result = service.requestManualTrigger("daily-mirror", "admin-sub");
        assertThat(result).isTrue();
    }

    @Test
    void requestManualTrigger_existingPendingOrClaimed_returnsFalse() {
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(testSource()));
        when(repository.atomicEnqueueTrigger(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        boolean result = service.requestManualTrigger("daily-mirror", "admin-sub");
        assertThat(result).isFalse();
    }

    // === Atomic Claim ===

    @Test
    void claimNextPending_atomicTransition() {
        IngestionTriggerRequest claimed = new IngestionTriggerRequest(
                "t1", "src1", "daily-mirror", "admin-sub", NOW,
                IngestionTriggerRequest.STATUS_CLAIMED, 1, null, NOW, "w1", null, null
        );
        when(repository.claimNextPendingTrigger("w1", NOW)).thenReturn(Optional.of(claimed));

        Optional<IngestionTriggerRequest> result = service.claimNextPending("w1");
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(IngestionTriggerRequest.STATUS_CLAIMED);
    }

    @Test
    void claimNextPending_noPending_returnsEmpty() {
        when(repository.claimNextPendingTrigger("w1", NOW)).thenReturn(Optional.empty());

        Optional<IngestionTriggerRequest> result = service.claimNextPending("w1");
        assertThat(result).isEmpty();
    }

    // === ACTIVE_RUN Retry ===

    @Test
    void retryLater_withinBound_setsPendingWithNextAttemptAt() {
        IngestionTriggerRequest trigger = new IngestionTriggerRequest(
                "t1", "src1", "daily-mirror", "admin-sub", NOW,
                IngestionTriggerRequest.STATUS_CLAIMED, 2, null, NOW, "w1", null, null
        );
        when(repository.findById("t1")).thenReturn(Optional.of(trigger));

        service.retryLater("t1");

        ArgumentCaptor<IngestionTriggerRequest> captor = ArgumentCaptor.forClass(IngestionTriggerRequest.class);
        verify(repository).save(captor.capture());

        IngestionTriggerRequest saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(IngestionTriggerRequest.STATUS_PENDING);
        assertThat(saved.nextAttemptAt()).isNotNull();
        assertThat(saved.nextAttemptAt()).isAfter(NOW);
    }

    @Test
    void retryLater_atMaxAttempts_becomesCancelled() {
        IngestionTriggerRequest trigger = new IngestionTriggerRequest(
                "t1", "src1", "daily-mirror", "admin-sub", NOW,
                IngestionTriggerRequest.STATUS_CLAIMED, 5, null, NOW, "w1", null, null
        );
        when(repository.findById("t1")).thenReturn(Optional.of(trigger));

        service.retryLater("t1");

        ArgumentCaptor<IngestionTriggerRequest> captor = ArgumentCaptor.forClass(IngestionTriggerRequest.class);
        verify(repository).save(captor.capture());

        IngestionTriggerRequest saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(IngestionTriggerRequest.STATUS_CANCELLED);
    }

    // === Trigger RunId Linkage ===

    @Test
    void markRunStarted_setsRunId() {
        IngestionTriggerRequest trigger = new IngestionTriggerRequest(
                "t1", "src1", "daily-mirror", "admin-sub", NOW,
                IngestionTriggerRequest.STATUS_CLAIMED, 1, null, NOW, "w1", null, null
        );
        when(repository.findById("t1")).thenReturn(Optional.of(trigger));

        service.markRunStarted("t1", "run-123");

        ArgumentCaptor<IngestionTriggerRequest> captor = ArgumentCaptor.forClass(IngestionTriggerRequest.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().runId()).isEqualTo("run-123");
    }

    // === Complete / Fail ===

    @Test
    void complete_transitionsToCompleted() {
        IngestionTriggerRequest trigger = new IngestionTriggerRequest(
                "t1", "src1", "daily-mirror", "admin-sub", NOW,
                IngestionTriggerRequest.STATUS_CLAIMED, 1, null, NOW, "w1", "run-1", null
        );
        when(repository.findById("t1")).thenReturn(Optional.of(trigger));

        service.complete("t1");

        ArgumentCaptor<IngestionTriggerRequest> captor = ArgumentCaptor.forClass(IngestionTriggerRequest.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(IngestionTriggerRequest.STATUS_COMPLETED);
        assertThat(captor.getValue().completedAt()).isNotNull();
    }

    @Test
    void fail_transitionsToFailed() {
        IngestionTriggerRequest trigger = new IngestionTriggerRequest(
                "t1", "src1", "daily-mirror", "admin-sub", NOW,
                IngestionTriggerRequest.STATUS_CLAIMED, 1, null, NOW, "w1", "run-1", null
        );
        when(repository.findById("t1")).thenReturn(Optional.of(trigger));

        service.fail("t1");

        ArgumentCaptor<IngestionTriggerRequest> captor = ArgumentCaptor.forClass(IngestionTriggerRequest.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(IngestionTriggerRequest.STATUS_FAILED);
    }

    @Test
    void complete_unknownTrigger_doesNothing() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        service.complete("nonexistent");

        verify(repository, never()).save(any());
    }
}
