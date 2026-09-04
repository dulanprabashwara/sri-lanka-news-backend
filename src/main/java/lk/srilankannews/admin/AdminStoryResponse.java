package lk.srilankannews.admin;

import java.time.Instant;
import lk.srilankannews.story.Story;

public record AdminStoryResponse(
        String id,
        String displayTitle,
        String category,
        int articleCount,
        int sourceCount,
        Instant firstPublishedAt,
        Instant lastPublishedAt,
        boolean representativeMediaPresent,
        String matchingVersion) {

    public static AdminStoryResponse from(Story story) {
        return new AdminStoryResponse(
                story.id(),
                story.canonicalTitle(),
                story.category() != null ? story.category().name() : null,
                (int) story.articleCount(),
                story.sourceIds().size(),
                story.firstPublishedAt(),
                story.lastPublishedAt(),
                story.representativeMedia() != null,
                story.matchingVersion()
        );
    }
}
