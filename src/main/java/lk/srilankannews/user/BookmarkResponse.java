package lk.srilankannews.user;

import java.time.Instant;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.story.api.StorySummaryResponse;

public record BookmarkResponse(
        String bookmarkId,
        BookmarkTargetType targetType,
        String targetId,
        Instant createdAt,
        ArticleResponse article,
        StorySummaryResponse story
) {
}
