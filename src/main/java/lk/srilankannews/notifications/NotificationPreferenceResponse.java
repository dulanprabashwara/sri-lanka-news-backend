package lk.srilankannews.notifications;

import java.time.LocalTime;

public record NotificationPreferenceResponse(
        boolean inAppEnabled,
        boolean emailEnabled,
        boolean sourceFollowNotificationsEnabled,
        boolean topicFollowNotificationsEnabled,
        boolean storyUpdateNotificationsEnabled,
        boolean quietHoursEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,
        String timezone,
        boolean emailAvailable
) {
    public NotificationPreferenceResponse(NotificationPreference prefs, boolean emailAvailable) {
        this(
                prefs.inAppEnabled(),
                prefs.emailEnabled(),
                prefs.sourceFollowNotificationsEnabled(),
                prefs.topicFollowNotificationsEnabled(),
                prefs.storyUpdateNotificationsEnabled(),
                prefs.quietHoursEnabled(),
                prefs.quietHoursStart(),
                prefs.quietHoursEnd(),
                prefs.timezone(),
                emailAvailable
        );
    }
}
