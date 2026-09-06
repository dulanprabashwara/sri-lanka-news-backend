package lk.srilankannews.retention;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.retention")
public record RetentionProperties(
        @NotNull @Valid NotificationsRetention notifications,
        @NotNull @Valid NotificationOutboxRetention notificationOutbox,
        @NotNull @Valid IngestionRunsRetention ingestionRuns,
        @NotNull @Valid IngestionTriggersRetention ingestionTriggers,
        @NotNull @Valid AdminAuditRetention adminAudit,
        @NotNull @Valid ResolvedDlqRetention resolvedDlq
) {
    public RetentionProperties {
        notifications = notifications == null ? NotificationsRetention.defaults() : notifications;
        notificationOutbox = notificationOutbox == null ? NotificationOutboxRetention.defaults() : notificationOutbox;
        ingestionRuns = ingestionRuns == null ? IngestionRunsRetention.defaults() : ingestionRuns;
        ingestionTriggers = ingestionTriggers == null ? IngestionTriggersRetention.defaults() : ingestionTriggers;
        adminAudit = adminAudit == null ? AdminAuditRetention.defaults() : adminAudit;
        resolvedDlq = resolvedDlq == null ? ResolvedDlqRetention.defaults() : resolvedDlq;

        if (notifications.unreadMaxDays() < notifications.readDays()) {
            throw new IllegalArgumentException(String.format(
                    "Unread notification max retention (%d days) cannot be shorter than read notification retention (%d days)",
                    notifications.unreadMaxDays(), notifications.readDays()));
        }

        if (notificationOutbox.failedDays() < notificationOutbox.publishedDays()) {
            throw new IllegalArgumentException(String.format(
                    "Failed notification outbox retention (%d days) cannot be shorter than published notification outbox retention (%d days)",
                    notificationOutbox.failedDays(), notificationOutbox.publishedDays()));
        }
    }

    public static RetentionProperties defaults() {
        return new RetentionProperties(
                NotificationsRetention.defaults(),
                NotificationOutboxRetention.defaults(),
                IngestionRunsRetention.defaults(),
                IngestionTriggersRetention.defaults(),
                AdminAuditRetention.defaults(),
                ResolvedDlqRetention.defaults()
        );
    }

    public record NotificationsRetention(
            @Min(value = 30, message = "Read notification retention must be at least 30 days")
            int readDays,
            @Min(value = 30, message = "Unread notification max retention must be at least 30 days")
            int unreadMaxDays
    ) {
        public static NotificationsRetention defaults() {
            return new NotificationsRetention(180, 365);
        }
    }

    public record NotificationOutboxRetention(
            @Min(value = 7, message = "Published notification outbox retention must be at least 7 days")
            int publishedDays,
            @Min(value = 7, message = "Failed notification outbox retention must be at least 7 days")
            int failedDays
    ) {
        public static NotificationOutboxRetention defaults() {
            return new NotificationOutboxRetention(30, 90);
        }
    }

    public record IngestionRunsRetention(
            @Min(value = 30, message = "Terminal ingestion run retention must be at least 30 days")
            int terminalDays
    ) {
        public static IngestionRunsRetention defaults() {
            return new IngestionRunsRetention(90);
        }
    }

    public record IngestionTriggersRetention(
            @Min(value = 7, message = "Processed ingestion trigger retention must be at least 7 days")
            int processedDays
    ) {
        public static IngestionTriggersRetention defaults() {
            return new IngestionTriggersRetention(30);
        }
    }

    public record AdminAuditRetention(
            @Min(value = 30, message = "Admin audit event retention must be at least 30 days")
            int days
    ) {
        public static AdminAuditRetention defaults() {
            return new AdminAuditRetention(365);
        }
    }

    /**
     * Future/Inactive policy property for resolved DLQ entry retention.
     * Note: Trimming is currently UNSAFE and INACTIVE until DLQ resolution tracking exists.
     */
    public record ResolvedDlqRetention(
            @Min(value = 7, message = "Resolved DLQ retention must be at least 7 days")
            int days
    ) {
        public static ResolvedDlqRetention defaults() {
            return new ResolvedDlqRetention(90);
        }
    }
}
