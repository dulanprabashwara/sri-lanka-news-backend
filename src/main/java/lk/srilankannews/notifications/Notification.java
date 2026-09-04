package lk.srilankannews.notifications;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "user_createdAt_idx", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "user_readAt_idx", def = "{'userId': 1, 'readAt': 1}"),
        @CompoundIndex(name = "email_status_nextAttempt_idx", def = "{'emailDelivery.status': 1, 'emailDelivery.nextAttemptAt': 1}")
})
public record Notification(
        @Id String id,
        String userId,
        NotificationType type,
        String storyId,
        String triggeringArticleId,
        String sourceId,
        String sourceSlug,
        String sourceName,
        List<NotificationReason> reasons,
        String title,
        String message,
        String linkPath,
        String eventVersion,
        @Indexed(unique = true) String dedupeKey,
        Instant createdAt,
        Instant readAt,
        EmailDelivery emailDelivery
) {
    public enum NotificationType {
        STORY_ACTIVITY,
        SYSTEM
    }

    public enum NotificationReason {
        FOLLOWED_SOURCE,
        FOLLOWED_TOPIC
    }

    public record EmailDelivery(
            DeliveryStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            Instant sentAt,
            String lastErrorCode
    ) {
        public enum DeliveryStatus {
            NOT_REQUESTED,
            PENDING,
            DEFERRED,
            SENDING,
            SENT,
            RETRYING,
            FAILED
        }
    }
}
