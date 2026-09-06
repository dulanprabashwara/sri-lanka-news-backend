package lk.srilankannews.notifications;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notification_events")
@CompoundIndexes({
        @CompoundIndex(name = "status_nextAttemptAt_idx", def = "{'status': 1, 'nextAttemptAt': 1}"),
        @CompoundIndex(name = "article_version_idx", def = "{'articleId': 1, 'eventVersion': 1}", unique = true)
})
public record NotificationEvent(
        @Id String id,
        String articleId,
        String storyId,
        String sourceId,
        Instant occurredAt,
        String eventVersion,
        EventStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant publishedAt,
        Instant processedAt,
        String lastErrorCode,
        @JsonIgnore Instant expiresAt
) {
    public enum EventStatus {
        PENDING,
        PUBLISHED,
        PROCESSING,
        PROCESSED,
        RETRYING,
        FAILED
    }
}
