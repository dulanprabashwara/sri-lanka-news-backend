package lk.srilankannews.story.api;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.api.ArticleResponse;

public record StoryDetailResponse(
        String id,
        String canonicalTitle,
        ArticleCategory category,
        Instant firstPublishedAt,
        Instant lastPublishedAt,
        long articleCount,
        int sourceCount,
        List<ArticleResponse> articles
) {
    public StoryDetailResponse {
        articles = List.copyOf(articles);
    }
}
