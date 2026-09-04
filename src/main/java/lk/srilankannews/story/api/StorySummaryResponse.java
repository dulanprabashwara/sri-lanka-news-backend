package lk.srilankannews.story.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lk.srilankannews.article.ArticleCategory;

public record StorySummaryResponse(
        String id,
        String canonicalTitle,
        ArticleCategory category,
        Instant firstPublishedAt,
        Instant lastPublishedAt,
        long articleCount,
        int sourceCount,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        StoryRepresentativeMediaResponse representativeMedia,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        LocalizedStoryContentResponse localizedContent
) {
    public StorySummaryResponse(
            String id, String canonicalTitle, ArticleCategory category,
            Instant firstPublishedAt, Instant lastPublishedAt,
            long articleCount, int sourceCount) {
        this(id, canonicalTitle, category, firstPublishedAt, lastPublishedAt,
                articleCount, sourceCount, null, null);
    }
}
