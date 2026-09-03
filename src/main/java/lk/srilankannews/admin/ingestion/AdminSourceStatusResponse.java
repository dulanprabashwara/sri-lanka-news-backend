package lk.srilankannews.admin.ingestion;

import java.time.Instant;
import lk.srilankannews.common.domain.Language;

public record AdminSourceStatusResponse(
        String sourceId,
        String sourceSlug,
        String displayName,
        Language language,
        boolean enabled,
        int intervalMinutes,
        int jitterSeconds,
        String health,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        String lastRunStatus,
        String lastRunId,
        Integer lastDiscovered,
        Integer lastSubmitted,
        Integer lastSucceeded,
        Integer lastFailed,
        int consecutiveFailures
) {
}
