package lk.srilankannews.notifications;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user_notification_preferences")
public record NotificationPreference(
        @Id String userId,
        boolean inAppEnabled,
        String email,
        boolean emailEnabled,
        boolean sourceFollowNotificationsEnabled,
        boolean topicFollowNotificationsEnabled,
        boolean storyUpdateNotificationsEnabled,
        boolean quietHoursEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,
        String timezone,
        Instant createdAt,
        Instant updatedAt
) {
    public NotificationPreference {
        if (quietHoursEnabled) {
            if (quietHoursStart == null || quietHoursEnd == null || timezone == null) {
                throw new IllegalArgumentException("Quiet hours start, end, and timezone must be provided if quiet hours are enabled");
            }
            try {
                ZoneId.of(timezone);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid timezone identifier: " + timezone);
            }
        }
    }

    public static NotificationPreference defaultPreferences(String userId, Instant now) {
        return new NotificationPreference(
                userId,
                true,
                null,
                false,
                true,
                true,
                true,
                false,
                null,
                null,
                null,
                now,
                now
        );
    }
}
