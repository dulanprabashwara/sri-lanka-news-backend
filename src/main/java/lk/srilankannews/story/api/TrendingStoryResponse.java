package lk.srilankannews.story.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;

public record TrendingStoryResponse(
        String id,
        String canonicalTitle,
        ArticleCategory category,
        Instant firstPublishedAt,
        Instant lastPublishedAt,
        long articleCount,
        int sourceCount,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        LocalizedStoryContentResponse localizedContent,
        List<TrendingReason> reasons) {

    public TrendingStoryResponse {
        reasons = List.copyOf(reasons);
    }
}
