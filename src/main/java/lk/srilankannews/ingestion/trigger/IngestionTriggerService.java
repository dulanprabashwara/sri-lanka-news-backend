package lk.srilankannews.ingestion.trigger;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lk.srilankannews.retention.RetentionPolicyService;
import lk.srilankannews.ingestion.settings.IngestionSourceSettingsService;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.springframework.stereotype.Service;

@Service
public class IngestionTriggerService {

    private final IngestionTriggerRequestRepository repository;
    private final SourceService sourceService;
    private final RetentionPolicyService retentionPolicyService;
    private final IngestionSourceSettingsService settingsService;
    private final Clock clock;

    public IngestionTriggerService(IngestionTriggerRequestRepository repository,
                                   SourceService sourceService,
                                   IngestionSourceSettingsService settingsService,
                                   RetentionPolicyService retentionPolicyService,
                                   Clock clock) {
        this.repository = repository;
        this.sourceService = sourceService;
        this.settingsService = settingsService;
        this.retentionPolicyService = retentionPolicyService;
        this.clock = clock;
    }

    public boolean requestManualTrigger(String sourceSlug, String adminUserId) {
        Source source = sourceService.findBySlug(sourceSlug)
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));
        if (!settingsService.isEnabled(sourceSlug)) {
            throw new IllegalArgumentException("Ingestion is disabled for source " + sourceSlug);
        }

        Optional<IngestionTriggerRequest> enqueued = repository.atomicEnqueueTrigger(
                source.id(), sourceSlug, adminUserId, Instant.now(clock)
        );

        return enqueued.isPresent();
    }

    public Optional<IngestionTriggerRequest> claimNextPending(String workerId) {
        return repository.claimNextPendingTrigger(workerId, Instant.now(clock));
    }

    public void markRunStarted(String triggerId, String runId) {
        repository.findById(triggerId).ifPresent(trigger -> {
            repository.save(trigger.withRunId(runId));
        });
    }

    public void retryLater(String triggerId) {
        repository.findById(triggerId).ifPresent(trigger -> {
            // Bounded retries: max 5 attempts, exponential-ish backoff
            if (trigger.attemptCount() >= 5) {
                Instant now = Instant.now(clock);
                Instant expiresAt = retentionPolicyService
                        .calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_CANCELLED, now).orElse(null);
                repository.save(trigger.withCancelled(now, expiresAt));
            } else {
                Instant next = Instant.now(clock).plus(trigger.attemptCount() * 15L, ChronoUnit.SECONDS);
                repository.save(trigger.withRetryPending(next));
            }
        });
    }

    public void complete(String triggerId) {
        repository.findById(triggerId).ifPresent(trigger -> {
            Instant now = Instant.now(clock);
            Instant expiresAt = retentionPolicyService
                    .calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_CANCELLED.equals(trigger.status()) 
                            ? IngestionTriggerRequest.STATUS_CANCELLED 
                            : IngestionTriggerRequest.STATUS_COMPLETED, now).orElse(null);
            repository.save(trigger.withCompleted(now, expiresAt));
        });
    }

    public void fail(String triggerId) {
        repository.findById(triggerId).ifPresent(trigger -> {
            Instant now = Instant.now(clock);
            Instant expiresAt = retentionPolicyService
                    .calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_FAILED, now).orElse(null);
            repository.save(trigger.withFailed(now, expiresAt));
        });
    }
}
