package lk.srilankannews.story.api;

import java.time.Instant;
import lk.srilankannews.article.ArticleCategory;

public record StorySummaryResponse(
        String id,
        String canonicalTitle,
        ArticleCategory category,
        Instant firstPublishedAt,
        Instant lastPublishedAt,
        long articleCount,
        int sourceCount
) {
}
