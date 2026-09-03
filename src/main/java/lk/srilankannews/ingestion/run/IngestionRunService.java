package lk.srilankannews.ingestion.run;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.ingestion.run.api.ClaimRequest;
import lk.srilankannews.ingestion.run.api.ClaimResponse;
import lk.srilankannews.ingestion.run.api.CompleteRequest;
import lk.srilankannews.ingestion.run.api.FailRequest;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class IngestionRunService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionRunService.class);
    
    // Default fallback if Python doesn't specify, but normally Python determines heartbeat frequency
    private static final long DEFAULT_LEASE_SECONDS = 600; 

    private final IngestionRunRepository runRepository;
    private final IngestionSourceLeaseRepository leaseRepository;
    private final SourceService sourceService;
    private final Clock clock;

    public IngestionRunService(
            IngestionRunRepository runRepository,
            IngestionSourceLeaseRepository leaseRepository,
            SourceService sourceService,
            Clock clock
    ) {
        this.runRepository = runRepository;
        this.leaseRepository = leaseRepository;
        this.sourceService = sourceService;
        this.clock = clock;
    }

    public ClaimResponse claim(ClaimRequest request) {
        Source source = sourceService.findBySlug(request.sourceSlug())
                .orElseThrow(() -> new ResourceNotFoundException("Source"));

        if (!source.enabled()) {
            return ClaimResponse.activeRun();
        }

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(DEFAULT_LEASE_SECONDS, ChronoUnit.SECONDS);
        String newRunId = new ObjectId().toHexString();

        // 1. Interrupt expired orphan runs unconditionally (handles missing/mismatched lease cases)
        interruptOrphanedRuns(source.id(), now);

        // 2. Fetch current lease to identify expired run ID for direct lookup
        Optional<IngestionSourceLease> existingLease = leaseRepository.findById(source.id());
        String expiredRunIdToInterrupt = null;
        if (existingLease.isPresent() && existingLease.get().expiresAt().isBefore(now)) {
            expiredRunIdToInterrupt = existingLease.get().runId();
        }

        // 3. Atomically acquire
        Optional<IngestionSourceLease> acquiredLease;
        try {
            acquiredLease = leaseRepository.acquireLease(
                    source.id(), newRunId, request.workerId(), expiresAt, now);
        } catch (DuplicateKeyException e) {
            return ClaimResponse.activeRun();
        }

        if (acquiredLease.isEmpty() || !newRunId.equals(acquiredLease.get().runId())) {
            return ClaimResponse.activeRun();
        }

        // 4. Create RUNNING IngestionRun
        IngestionRun newRun = IngestionRun.createRunning(
                newRunId,
                source.id(),
                source.slug(),
                request.triggerType(),
                request.scheduledFor(),
                request.workerId(),
                expiresAt,
                now
        );

        try {
            runRepository.save(newRun);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to save IngestionRun, releasing lease for runId={}", newRunId, e);
            leaseRepository.releaseLease(source.id(), newRunId);
            throw e;
        }

        // 5. Interrupt specific stale run if known by lease (handles normal case)
        if (expiredRunIdToInterrupt != null) {
            interruptSpecificRun(expiredRunIdToInterrupt, now);
        }

        return ClaimResponse.success(newRunId, expiresAt);
    }

    private void interruptSpecificRun(String runId, Instant now) {
        // Direct lookup by lease.runId (normal case after ID fix)
        runRepository.findById(runId).ifPresent(run -> {
            if (run.status() == IngestionRunStatus.RUNNING) {
                runRepository.save(run.withInterrupted(now));
                LOGGER.info("Interrupted stale ingestion run runId={}", runId);
            }
        });
    }

    private void interruptOrphanedRuns(String sourceId, Instant now) {
        // Safe orphan cleanup: finds runs for EXACT source, ONLY RUNNING, and STRICTLY EXPIRED.
        // Runs before the new run is saved, so it can never touch the new run.
        // Handles legacy cases where no lease exists, or lease runId doesn't match IngestionRun._id.
        runRepository.findBySourceIdAndStatusAndLeaseExpiresAtBefore(
                sourceId, IngestionRunStatus.RUNNING, now
        ).forEach(orphanedRun -> {
            runRepository.save(orphanedRun.withInterrupted(now));
            LOGGER.info("Interrupted orphaned stale run runId={} sourceId={}", orphanedRun.id(), sourceId);
        });
    }

    public Instant heartbeat(String runId) {
        Instant now = Instant.now(clock);
        IngestionRun run = getRun(runId);
        
        if (run.status() != IngestionRunStatus.RUNNING) {
            throw new IngestionRunConflictException("Run is not in RUNNING state");
        }
        
        Instant newExpiresAt = now.plus(DEFAULT_LEASE_SECONDS, ChronoUnit.SECONDS);
        
        Optional<IngestionSourceLease> extended = leaseRepository.extendLease(
                run.sourceId(), runId, newExpiresAt);
                
        if (extended.isEmpty()) {
            throw new IngestionRunConflictException("Lease is no longer owned by this run");
        }

        runRepository.save(run.withHeartbeat(newExpiresAt, now));
        return newExpiresAt;
    }

    public void complete(String runId, CompleteRequest request) {
        Instant now = Instant.now(clock);
        IngestionRun run = getRun(runId);
        
        if (run.status() == IngestionRunStatus.COMPLETED) {
            return; // Idempotent
        }
        if (run.status() != IngestionRunStatus.RUNNING) {
            throw new IngestionRunConflictException("Cannot complete run from status " + run.status());
        }

        IngestionRun completed = run.withCompleted(
                request.articlesDiscovered(),
                request.articlesSubmitted(),
                request.articlesSucceeded(),
                request.articlesFailed(),
                now
        );
        runRepository.save(completed);
        leaseRepository.releaseLease(run.sourceId(), runId);
    }

    public void fail(String runId, FailRequest request) {
        Instant now = Instant.now(clock);
        IngestionRun run = getRun(runId);
        
        if (run.status() == IngestionRunStatus.FAILED) {
            return; // Idempotent
        }
        if (run.status() != IngestionRunStatus.RUNNING) {
            throw new IngestionRunConflictException("Cannot fail run from status " + run.status());
        }

        IngestionRun failed = run.withFailed(
                request.articlesDiscovered(),
                request.articlesSubmitted(),
                request.articlesSucceeded(),
                request.articlesFailed(),
                request.errorCode(),
                request.errorMessage(),
                now
        );
        runRepository.save(failed);
        leaseRepository.releaseLease(run.sourceId(), runId);
    }

    private IngestionRun getRun(String runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("IngestionRun"));
    }
}
