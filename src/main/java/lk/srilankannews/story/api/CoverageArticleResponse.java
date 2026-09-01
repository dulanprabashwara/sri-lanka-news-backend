package lk.srilankannews.story.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lk.srilankannews.article.api.LocalizedContentResponse;
import lk.srilankannews.common.domain.Language;

public record CoverageArticleResponse(
        String id,
        String title,
        String summary,
        Language originalLanguage,
        Instant publishedAt,
        String originalUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalizedContentResponse localizedContent
) {
    public CoverageArticleResponse(
            String id, String title, String summary, Language originalLanguage,
            Instant publishedAt, String originalUrl) {
        this(id, title, summary, originalLanguage, publishedAt, originalUrl, null);
    }
}
