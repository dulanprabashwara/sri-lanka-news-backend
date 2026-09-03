package lk.srilankannews.admin.ingestion;

import java.time.Instant;
import lk.srilankannews.ingestion.run.IngestionRun;

public record AdminRunHistoryResponse(
        String runId,
        String sourceSlug,
        String triggerType,
        String status,
        Instant scheduledFor,
        Instant startedAt,
        Instant finishedAt,
        Integer articlesDiscovered,
        Integer articlesSubmitted,
        Integer articlesSucceeded,
        Integer articlesFailed,
        String safeErrorCode,
        String safeErrorMessage
) {
    public static AdminRunHistoryResponse from(IngestionRun run) {
        return new AdminRunHistoryResponse(
                run.id(),
                run.sourceSlug(),
                run.triggerType().name(),
                run.status().name(),
                run.scheduledFor(),
                run.startedAt(),
                run.finishedAt(),
                run.articlesDiscovered(),
                run.articlesSubmitted(),
                run.articlesSucceeded(),
                run.articlesFailed(),
                run.safeErrorCode(),
                run.safeErrorMessage()
        );
    }
}
