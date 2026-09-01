package lk.srilankannews.story.api;

import java.time.Instant;
import lk.srilankannews.common.domain.Language;

public record CoverageArticleResponse(
        String id,
        String title,
        String summary,
        Language originalLanguage,
        Instant publishedAt,
        String originalUrl
) {
}
