package lk.srilankannews.ingestion.trigger;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.springframework.stereotype.Service;

@Service
public class IngestionTriggerService {

    private final IngestionTriggerRequestRepository repository;
    private final SourceService sourceService;
    private final Clock clock;

    public IngestionTriggerService(IngestionTriggerRequestRepository repository, SourceService sourceService, Clock clock) {
        this.repository = repository;
        this.sourceService = sourceService;
        this.clock = clock;
    }

    public boolean requestManualTrigger(String sourceSlug, String adminUserId) {
        Source source = sourceService.findBySlug(sourceSlug)
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));

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
                repository.save(trigger.withCancelled(Instant.now(clock)));
            } else {
                Instant next = Instant.now(clock).plus(trigger.attemptCount() * 15L, ChronoUnit.SECONDS);
                repository.save(trigger.withRetryPending(next));
            }
        });
    }

    public void complete(String triggerId) {
        repository.findById(triggerId).ifPresent(trigger -> {
            repository.save(trigger.withCompleted(Instant.now(clock)));
        });
    }

    public void fail(String triggerId) {
        repository.findById(triggerId).ifPresent(trigger -> {
            repository.save(trigger.withFailed(Instant.now(clock)));
        });
    }
}
