package lk.srilankannews.story.api;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        List<ArticleResponse> articles,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        StoryRepresentativeMediaResponse representativeMedia,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        LocalizedStoryContentResponse localizedContent
) {
    public StoryDetailResponse {
        articles = List.copyOf(articles);
    }

    public StoryDetailResponse(
            String id, String canonicalTitle, ArticleCategory category,
            Instant firstPublishedAt, Instant lastPublishedAt, long articleCount,
            int sourceCount, List<ArticleResponse> articles) {
        this(id, canonicalTitle, category, firstPublishedAt, lastPublishedAt,
                articleCount, sourceCount, articles, null, null);
    }
}
