package lk.srilankannews.ingestion.trigger;

import java.time.Instant;
import java.util.Optional;

public interface CustomIngestionTriggerRequestRepository {
    Optional<IngestionTriggerRequest> claimNextPendingTrigger(String workerId, Instant now);
    Optional<IngestionTriggerRequest> atomicEnqueueTrigger(String sourceId, String sourceSlug, String requestedBy, Instant now);
}
