package lk.srilankannews.story.ask;

import java.time.Instant;

public record AskStoryCitationResponse(
        int number,
        String articleId,
        String title,
        AskStorySourceResponse source,
        Instant publishedAt,
        String originalUrl) {
}
