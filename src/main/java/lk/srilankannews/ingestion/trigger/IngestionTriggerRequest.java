package lk.srilankannews.ingestion.trigger;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ingestion_trigger_requests")
@CompoundIndexes({
        @CompoundIndex(name = "idx_status_nextAttemptAt", def = "{'status': 1, 'nextAttemptAt': 1}"),
        @CompoundIndex(name = "idx_sourceId_status", def = "{'sourceId': 1, 'status': 1}")
})
public record IngestionTriggerRequest(
        @Id String id,
        String sourceId,
        String sourceSlug,
        String requestedBy,
        Instant requestedAt,
        String status, // PENDING, CLAIMED, COMPLETED, FAILED, CANCELLED
        int attemptCount,
        Instant nextAttemptAt,
        Instant claimedAt,
        String workerId,
        String runId,
        Instant completedAt
) {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    
    public IngestionTriggerRequest withClaimed(String workerId, Instant now) {
        return new IngestionTriggerRequest(
                id, sourceId, sourceSlug, requestedBy, requestedAt,
                STATUS_CLAIMED, attemptCount + 1, null, now, workerId, runId, null
        );
    }
    
    public IngestionTriggerRequest withRunId(String runId) {
        return new IngestionTriggerRequest(
                id, sourceId, sourceSlug, requestedBy, requestedAt,
                status, attemptCount, nextAttemptAt, claimedAt, workerId, runId, completedAt
        );
    }
    
    public IngestionTriggerRequest withRetryPending(Instant nextAttemptAt) {
        return new IngestionTriggerRequest(
                id, sourceId, sourceSlug, requestedBy, requestedAt,
                STATUS_PENDING, attemptCount, nextAttemptAt, null, null, null, null
        );
    }
    
    public IngestionTriggerRequest withCompleted(Instant now) {
        return new IngestionTriggerRequest(
                id, sourceId, sourceSlug, requestedBy, requestedAt,
                STATUS_COMPLETED, attemptCount, nextAttemptAt, claimedAt, workerId, runId, now
        );
    }
    
    public IngestionTriggerRequest withFailed(Instant now) {
        return new IngestionTriggerRequest(
                id, sourceId, sourceSlug, requestedBy, requestedAt,
                STATUS_FAILED, attemptCount, nextAttemptAt, claimedAt, workerId, runId, now
        );
    }
    
    public IngestionTriggerRequest withCancelled(Instant now) {
        return new IngestionTriggerRequest(
                id, sourceId, sourceSlug, requestedBy, requestedAt,
                STATUS_CANCELLED, attemptCount, nextAttemptAt, claimedAt, workerId, runId, now
        );
    }
}
