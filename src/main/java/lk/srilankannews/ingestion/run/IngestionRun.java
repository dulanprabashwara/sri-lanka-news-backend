package lk.srilankannews.ingestion.run;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ingestion_runs")
@CompoundIndexes({
        @CompoundIndex(name = "idx_source_startedAt", def = "{'sourceId': 1, 'startedAt': -1}"),
        @CompoundIndex(name = "idx_status_startedAt", def = "{'status': 1, 'startedAt': -1}")
})
public record IngestionRun(
        @Id String id,
        String sourceId,
        String sourceSlug,
        IngestionTriggerType triggerType,
        IngestionRunStatus status,
        Instant scheduledFor,
        Instant startedAt,
        Instant finishedAt,
        String workerId,
        Instant leaseExpiresAt,
        Integer articlesDiscovered,
        Integer articlesSubmitted,
        Integer articlesSucceeded,
        Integer articlesFailed,
        String safeErrorCode,
        String safeErrorMessage,
        Instant createdAt,
        Instant updatedAt,
        @JsonIgnore Instant expiresAt
) {
    public static IngestionRun createRunning(
            String id,
            String sourceId,
            String sourceSlug,
            IngestionTriggerType triggerType,
            Instant scheduledFor,
            String workerId,
            Instant leaseExpiresAt,
            Instant now
    ) {
        return new IngestionRun(
                id,
                sourceId,
                sourceSlug,
                triggerType,
                IngestionRunStatus.RUNNING,
                scheduledFor,
                now,
                null,
                workerId,
                leaseExpiresAt,
                0, 0, 0, 0,
                null, null,
                now, now,
                null
        );
    }

    public IngestionRun withInterrupted(Instant now) {
        return withInterrupted(now, null);
    }

    public IngestionRun withInterrupted(Instant now, Instant expiresAt) {
        return new IngestionRun(
                id, sourceId, sourceSlug, triggerType,
                IngestionRunStatus.INTERRUPTED,
                scheduledFor, startedAt, now, workerId, leaseExpiresAt,
                articlesDiscovered, articlesSubmitted, articlesSucceeded, articlesFailed,
                "INTERRUPTED", "Run was interrupted by a new lease claim",
                createdAt, now,
                expiresAt
        );
    }

    public IngestionRun withHeartbeat(Instant newLeaseExpiresAt, Instant now) {
        return new IngestionRun(
                id, sourceId, sourceSlug, triggerType, status,
                scheduledFor, startedAt, finishedAt, workerId, newLeaseExpiresAt,
                articlesDiscovered, articlesSubmitted, articlesSucceeded, articlesFailed,
                safeErrorCode, safeErrorMessage,
                createdAt, now,
                expiresAt
        );
    }

    public IngestionRun withCompleted(
            int discovered, int submitted, int succeeded, int failed, Instant now
    ) {
        return withCompleted(discovered, submitted, succeeded, failed, now, null);
    }

    public IngestionRun withCompleted(
            int discovered, int submitted, int succeeded, int failed, Instant now, Instant expiresAt
    ) {
        return new IngestionRun(
                id, sourceId, sourceSlug, triggerType,
                IngestionRunStatus.COMPLETED,
                scheduledFor, startedAt, now, workerId, leaseExpiresAt,
                discovered, submitted, succeeded, failed,
                safeErrorCode, safeErrorMessage,
                createdAt, now,
                expiresAt
        );
    }

    public IngestionRun withFailed(
            int discovered, int submitted, int succeeded, int failed,
            String errorCode, String errorMessage, Instant now
    ) {
        return withFailed(discovered, submitted, succeeded, failed, errorCode, errorMessage, now, null);
    }

    public IngestionRun withFailed(
            int discovered, int submitted, int succeeded, int failed,
            String errorCode, String errorMessage, Instant now, Instant expiresAt
    ) {
        String boundErrorMessage = errorMessage != null && errorMessage.length() > 255 
                ? errorMessage.substring(0, 252) + "..." 
                : errorMessage;

        return new IngestionRun(
                id, sourceId, sourceSlug, triggerType,
                IngestionRunStatus.FAILED,
                scheduledFor, startedAt, now, workerId, leaseExpiresAt,
                discovered, submitted, succeeded, failed,
                errorCode, boundErrorMessage,
                createdAt, now,
                expiresAt
        );
    }
}
