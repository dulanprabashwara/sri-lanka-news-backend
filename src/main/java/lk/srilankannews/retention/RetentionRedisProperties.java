package lk.srilankannews.retention;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("news.retention.redis")
public record RetentionRedisProperties(
        boolean enabled,
        boolean apply,
        boolean scheduleEnabled,
        String scheduleCron,
        StreamRetentionConfig articleEvents,
        StreamRetentionConfig notificationEvents,
        DlqRetentionConfig resolvedDlq) {

    public RetentionRedisProperties {
        if (scheduleCron == null || scheduleCron.isBlank()) {
            scheduleCron = "0 0 */6 * * *";
        }
        if (articleEvents == null) {
            articleEvents = new StreamRetentionConfig(7);
        } else {
            articleEvents = articleEvents.validated();
        }
        if (notificationEvents == null) {
            notificationEvents = new StreamRetentionConfig(7);
        } else {
            notificationEvents = notificationEvents.validated();
        }
        if (resolvedDlq == null) {
            resolvedDlq = new DlqRetentionConfig(90, false);
        } else {
            resolvedDlq = resolvedDlq.validated();
        }
    }

    public record StreamRetentionConfig(int ackedHistoryDays) {
        public StreamRetentionConfig {
            if (ackedHistoryDays < 1) {
                ackedHistoryDays = 1; // Strict safety floor >= 1 day
            }
        }

        public StreamRetentionConfig validated() {
            return new StreamRetentionConfig(Math.max(1, ackedHistoryDays));
        }

        public Duration ackedHistoryDuration() {
            return Duration.ofDays(ackedHistoryDays);
        }
    }

    public record DlqRetentionConfig(int days, boolean enabled) {
        public DlqRetentionConfig {
            if (days < 7) {
                days = 7; // Floor 7 days if enabled in future
            }
        }

        public DlqRetentionConfig validated() {
            // DLQ automatic trimming MUST ALWAYS BE INACTIVE in DR6
            return new DlqRetentionConfig(Math.max(7, days), false);
        }
    }
}
