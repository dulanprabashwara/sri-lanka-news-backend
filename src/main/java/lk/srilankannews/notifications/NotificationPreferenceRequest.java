/*
 * File overview: Defines and validates input accepted by Notification Preference operations.
 * Area: in-app and email notifications.
 */
package lk.srilankannews.notifications;

import java.time.LocalTime;

public record NotificationPreferenceRequest(
        boolean inAppEnabled,
        boolean emailEnabled,
        boolean sourceFollowNotificationsEnabled,
        boolean topicFollowNotificationsEnabled,
        boolean storyUpdateNotificationsEnabled,
        boolean quietHoursEnabled,
        String quietHoursStart,
        String quietHoursEnd,
        String timezone
) {
}
