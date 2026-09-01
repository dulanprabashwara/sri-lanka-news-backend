package lk.srilankannews.story.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lk.srilankannews.article.api.LocalizedContentResponse;
import lk.srilankannews.common.domain.Language;

public record TimelineEventResponse(
        String articleId,
        String title,
        String summary,
        Language originalLanguage,
        Instant publishedAt,
        String originalUrl,
        TimelineSourceResponse source,
        long minutesFromFirstReport,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalizedContentResponse localizedContent
) {
    public TimelineEventResponse(
            String articleId, String title, String summary, Language originalLanguage,
            Instant publishedAt, String originalUrl, TimelineSourceResponse source,
            long minutesFromFirstReport) {
        this(articleId, title, summary, originalLanguage, publishedAt, originalUrl,
                source, minutesFromFirstReport, null);
    }
}
