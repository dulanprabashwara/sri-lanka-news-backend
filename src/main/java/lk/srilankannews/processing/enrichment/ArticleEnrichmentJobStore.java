package lk.srilankannews.processing.enrichment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArticleEnrichmentJobStore {
    void ensurePending(String articleId, Instant now);

    Optional<ArticleEnrichmentJob> claim(
            String articleId, Instant now, Duration leaseDuration, int maxAttempts);

    List<String> findDueArticleIds(Instant now, int maxAttempts, int limit);

    void markSucceeded(String articleId, String claimToken, Instant now);

    void markAlreadySucceeded(String articleId, Instant now);

    void markDeferred(
            String articleId, String claimToken, String failureKind,
            Instant nextRetryAt, Instant now);

    void markFailed(String articleId, String claimToken, String failureKind, Instant now);
}
