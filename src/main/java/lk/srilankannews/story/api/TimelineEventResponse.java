package lk.srilankannews.story.api;

import java.time.Instant;
import lk.srilankannews.common.domain.Language;

public record TimelineEventResponse(
        String articleId,
        String title,
        String summary,
        Language originalLanguage,
        Instant publishedAt,
        String originalUrl,
        TimelineSourceResponse source,
        long minutesFromFirstReport
) {
}
