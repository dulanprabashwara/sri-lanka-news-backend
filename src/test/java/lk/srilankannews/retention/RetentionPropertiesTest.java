package lk.srilankannews.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RetentionPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaultPropertiesBindCorrectly() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RetentionProperties.class);
            RetentionProperties props = context.getBean(RetentionProperties.class);

            assertThat(props.notifications().readDays()).isEqualTo(180);
            assertThat(props.notifications().unreadMaxDays()).isEqualTo(365);
            assertThat(props.notificationOutbox().publishedDays()).isEqualTo(30);
            assertThat(props.notificationOutbox().failedDays()).isEqualTo(90);
            assertThat(props.ingestionRuns().terminalDays()).isEqualTo(90);
            assertThat(props.ingestionTriggers().processedDays()).isEqualTo(30);
            assertThat(props.adminAudit().days()).isEqualTo(365);
            assertThat(props.resolvedDlq().days()).isEqualTo(90);
        });
    }

    @Test
    void customPropertyOverridesBindCorrectly() {
        contextRunner
                .withPropertyValues(
                        "news.retention.notifications.read-days=60",
                        "news.retention.notifications.unread-max-days=120",
                        "news.retention.notification-outbox.published-days=14",
                        "news.retention.notification-outbox.failed-days=60",
                        "news.retention.ingestion-runs.terminal-days=60",
                        "news.retention.ingestion-triggers.processed-days=14",
                        "news.retention.admin-audit.days=180",
                        "news.retention.resolved-dlq.days=30"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RetentionProperties.class);
                    RetentionProperties props = context.getBean(RetentionProperties.class);

                    assertThat(props.notifications().readDays()).isEqualTo(60);
                    assertThat(props.notifications().unreadMaxDays()).isEqualTo(120);
                    assertThat(props.notificationOutbox().publishedDays()).isEqualTo(14);
                    assertThat(props.notificationOutbox().failedDays()).isEqualTo(60);
                    assertThat(props.ingestionRuns().terminalDays()).isEqualTo(60);
                    assertThat(props.ingestionTriggers().processedDays()).isEqualTo(14);
                    assertThat(props.adminAudit().days()).isEqualTo(180);
                    assertThat(props.resolvedDlq().days()).isEqualTo(30);
                });
    }

    @Test
    void rejectedWhenReadDaysBelowMinimum() {
        contextRunner
                .withPropertyValues("news.retention.notifications.read-days=5")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectedWhenPublishedDaysBelowMinimum() {
        contextRunner
                .withPropertyValues("news.retention.notification-outbox.published-days=2")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectedWhenUnreadMaxShorterThanRead() {
        contextRunner
                .withPropertyValues(
                        "news.retention.notifications.read-days=180",
                        "news.retention.notifications.unread-max-days=60"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectedWhenFailedOutboxShorterThanPublished() {
        contextRunner
                .withPropertyValues(
                        "news.retention.notification-outbox.published-days=30",
                        "news.retention.notification-outbox.failed-days=10"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectedWhenAdminAuditBelowMinimum() {
        contextRunner
                .withPropertyValues("news.retention.admin-audit.days=10")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void directInstantiationCrossFieldValidation() {
        assertThatThrownBy(() -> new RetentionProperties(
                new RetentionProperties.NotificationsRetention(180, 60),
                RetentionProperties.NotificationOutboxRetention.defaults(),
                RetentionProperties.IngestionRunsRetention.defaults(),
                RetentionProperties.IngestionTriggersRetention.defaults(),
                RetentionProperties.AdminAuditRetention.defaults(),
                RetentionProperties.ResolvedDlqRetention.defaults()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unread notification max retention");

        assertThatThrownBy(() -> new RetentionProperties(
                RetentionProperties.NotificationsRetention.defaults(),
                new RetentionProperties.NotificationOutboxRetention(30, 10),
                RetentionProperties.IngestionRunsRetention.defaults(),
                RetentionProperties.IngestionTriggersRetention.defaults(),
                RetentionProperties.AdminAuditRetention.defaults(),
                RetentionProperties.ResolvedDlqRetention.defaults()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed notification outbox retention");
    }

    @Configuration
    @EnableConfigurationProperties(RetentionProperties.class)
    static class TestConfig {
    }
}
