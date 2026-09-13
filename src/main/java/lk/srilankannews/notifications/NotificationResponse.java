package lk.srilankannews.notifications;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.api.LocalizedContentResponse;

public record NotificationResponse(
        String id,
        String userId,
        Notification.NotificationType type,
        String storyId,
        String triggeringArticleId,
        String sourceId,
        String sourceSlug,
        String sourceName,
        List<Notification.NotificationReason> reasons,
        String title,
        String message,
        String linkPath,
        String eventVersion,
        String dedupeKey,
        Instant createdAt,
        Instant readAt,
        Notification.EmailDelivery emailDelivery,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalizedContentResponse localizedContent
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.id(),
                notification.userId(),
                notification.type(),
                notification.storyId(),
                notification.triggeringArticleId(),
                notification.sourceId(),
                notification.sourceSlug(),
                notification.sourceName(),
                notification.reasons(),
                notification.title(),
                notification.message(),
                notification.linkPath(),
                notification.eventVersion(),
                notification.dedupeKey(),
                notification.createdAt(),
                notification.readAt(),
                notification.emailDelivery(),
                null
        );
    }
}

