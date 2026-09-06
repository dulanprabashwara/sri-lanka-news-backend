package lk.srilankannews.retention;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lk.srilankannews.admin.audit.AdminAuditEvent;
import lk.srilankannews.analytics.AnalyticsEvent;
import lk.srilankannews.ingestion.run.IngestionRun;
import lk.srilankannews.ingestion.run.IngestionRunStatus;
import lk.srilankannews.ingestion.trigger.IngestionTriggerRequest;
import lk.srilankannews.notifications.Notification;
import lk.srilankannews.notifications.NotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MongoDbExpiryLifecycleTest {

    private RetentionPolicyService retentionPolicyService;
    private Clock fixedClock;
    private Instant now;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        RetentionProperties properties = RetentionProperties.defaults();
        retentionPolicyService = new RetentionPolicyService(properties);
        now = Instant.parse("2026-09-06T12:00:00Z");
        fixedClock = Clock.fixed(now, ZoneId.of("UTC"));

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("Safety Assertions (TTL Indexes)")
    class SafetyAssertions {

        @Test
        @DisplayName("Assert NO TTL index exists on new DR3 expiresAt fields AND existing analytics 30d TTL remains intact")
        void assertTtlIndexSafety() throws Exception {
            // Verify NO TTL index on DR3 target entity expiresAt fields
            Class<?>[] dr3Entities = {
                    Notification.class,
                    NotificationEvent.class,
                    IngestionRun.class,
                    IngestionTriggerRequest.class,
                    AdminAuditEvent.class
            };

            for (Class<?> clazz : dr3Entities) {
                Field expiresAtField = clazz.getDeclaredField("expiresAt");
                Indexed indexed = expiresAtField.getAnnotation(Indexed.class);
                assertThat(indexed)
                        .withFailMessage("Class %s field expiresAt MUST NOT have @Indexed TTL annotation!", clazz.getSimpleName())
                        .isNull();
            }

            // Verify existing AnalyticsEvent.receivedAt 30d TTL index remains intact
            Field analyticsField = AnalyticsEvent.class.getDeclaredField("receivedAt");
            Indexed analyticsIndexed = analyticsField.getAnnotation(Indexed.class);
            assertThat(analyticsIndexed).isNotNull();
            assertThat(analyticsIndexed.expireAfter()).isEqualTo("30d");
        }
    }

    @Nested
    @DisplayName("DTO Exposure Assertions (@JsonIgnore)")
    class DtoExposureAssertions {

        @Test
        @DisplayName("Ensure Notification expiresAt is annotated with @JsonIgnore and excluded from JSON output")
        void notificationExpiresAtIsIgnoredInJson() throws Exception {
            Field field = Notification.class.getDeclaredField("expiresAt");
            assertThat(field.isAnnotationPresent(JsonIgnore.class)).isTrue();

            Notification notification = new Notification(
                    "n1", "u1", Notification.NotificationType.STORY_ACTIVITY, "st1", "art1",
                    "src1", "slug1", "Source 1", List.of(), "Title", "Msg", "/path", "v1", "dedupe1",
                    now, now, null, now.plus(Duration.ofDays(30))
            );

            String json = objectMapper.writeValueAsString(notification);
            assertThat(json).doesNotContain("expiresAt");
        }

        @Test
        @DisplayName("Ensure NotificationEvent expiresAt is annotated with @JsonIgnore and excluded from JSON output")
        void notificationEventExpiresAtIsIgnoredInJson() throws Exception {
            Field field = NotificationEvent.class.getDeclaredField("expiresAt");
            assertThat(field.isAnnotationPresent(JsonIgnore.class)).isTrue();

            NotificationEvent event = new NotificationEvent(
                    "e1", "art1", "st1", "src1", now, "v1", NotificationEvent.EventStatus.PUBLISHED,
                    1, null, now, null, null, now.plus(Duration.ofDays(7))
            );

            String json = objectMapper.writeValueAsString(event);
            assertThat(json).doesNotContain("expiresAt");
        }

        @Test
        @DisplayName("Ensure IngestionRun expiresAt is annotated with @JsonIgnore and excluded from JSON output")
        void ingestionRunExpiresAtIsIgnoredInJson() throws Exception {
            Field field = IngestionRun.class.getDeclaredField("expiresAt");
            assertThat(field.isAnnotationPresent(JsonIgnore.class)).isTrue();

            IngestionRun run = IngestionRun.createRunning(
                    "r1", "src1", "slug1", lk.srilankannews.ingestion.run.IngestionTriggerType.SCHEDULED,
                    now, "w1", now.plusSeconds(600), now
            );

            String json = objectMapper.writeValueAsString(run);
            assertThat(json).doesNotContain("expiresAt");
        }

        @Test
        @DisplayName("Ensure IngestionTriggerRequest expiresAt is annotated with @JsonIgnore and excluded from JSON output")
        void ingestionTriggerRequestExpiresAtIsIgnoredInJson() throws Exception {
            Field field = IngestionTriggerRequest.class.getDeclaredField("expiresAt");
            assertThat(field.isAnnotationPresent(JsonIgnore.class)).isTrue();

            IngestionTriggerRequest trigger = new IngestionTriggerRequest(
                    "t1", "src1", "slug1", "adm1", now, IngestionTriggerRequest.STATUS_PENDING,
                    0, null, null, null, null, null, null
            );

            String json = objectMapper.writeValueAsString(trigger);
            assertThat(json).doesNotContain("expiresAt");
        }

        @Test
        @DisplayName("Ensure AdminAuditEvent expiresAt is annotated with @JsonIgnore and excluded from JSON output")
        void adminAuditEventExpiresAtIsIgnoredInJson() throws Exception {
            Field field = AdminAuditEvent.class.getDeclaredField("expiresAt");
            assertThat(field.isAnnotationPresent(JsonIgnore.class)).isTrue();

            AdminAuditEvent audit = new AdminAuditEvent(
                    "a1", "adm1", "TEST_EVENT", "src1", null, now, now.plus(Duration.ofDays(365))
            );

            String json = objectMapper.writeValueAsString(audit);
            assertThat(json).doesNotContain("expiresAt");
        }
    }

    @Nested
    @DisplayName("Retention Policy & Timestamp Calculations")
    class RetentionPolicyCalculations {

        @Test
        @DisplayName("Notification Unread Expiry: createdAt + unread-max-days (365d)")
        void notificationUnreadExpiry() {
            Optional<Instant> unreadExpiry = retentionPolicyService.calculateNotificationUnreadExpiry(now);
            assertThat(unreadExpiry).contains(now.plus(Duration.ofDays(365)));
        }

        @Test
        @DisplayName("Notification Read Expiry: readAt + read-days (180d)")
        void notificationReadExpiry() {
            Optional<Instant> readExpiry = retentionPolicyService.calculateNotificationReadExpiry(now);
            assertThat(readExpiry).contains(now.plus(Duration.ofDays(180)));
        }

        @Test
        @DisplayName("Custom configured unread retention duration works correctly")
        void customConfiguredUnreadDuration() {
            RetentionProperties customProperties = new RetentionProperties(
                    new RetentionProperties.NotificationsRetention(90, 180),
                    new RetentionProperties.NotificationOutboxRetention(7, 14),
                    new RetentionProperties.IngestionRunsRetention(30),
                    new RetentionProperties.IngestionTriggersRetention(14),
                    new RetentionProperties.AdminAuditRetention(90),
                    new RetentionProperties.ResolvedDlqRetention(14)
            );
            RetentionPolicyService customService = new RetentionPolicyService(customProperties);

            Optional<Instant> customUnreadExpiry = customService.calculateNotificationUnreadExpiry(now);
            assertThat(customUnreadExpiry).contains(now.plus(Duration.ofDays(180)));
        }

        @Test
        @DisplayName("NotificationEvent: PENDING/PROCESSING/RETRYING are null; PUBLISHED/PROCESSED expire in 30d; FAILED without terminal timestamp is null")
        void notificationEventRetentionPolicy() {
            assertThat(retentionPolicyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.PENDING, now)).isEmpty();
            assertThat(retentionPolicyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.PROCESSING, now)).isEmpty();
            assertThat(retentionPolicyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.RETRYING, now)).isEmpty();

            assertThat(retentionPolicyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.PUBLISHED, now))
                    .contains(now.plus(Duration.ofDays(30)));
            assertThat(retentionPolicyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.PROCESSED, now))
                    .contains(now.plus(Duration.ofDays(30)));

            // FAILED without terminal timestamp must return empty
            assertThat(retentionPolicyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.FAILED, null)).isEmpty();
        }

        @Test
        @DisplayName("IngestionRun: RUNNING is null; COMPLETED, FAILED, INTERRUPTED expire in 90 days from finishedAt; missing finishedAt is null")
        void ingestionRunRetentionPolicy() {
            assertThat(retentionPolicyService.calculateIngestionRunExpiry(IngestionRunStatus.RUNNING, now)).isEmpty();

            assertThat(retentionPolicyService.calculateIngestionRunExpiry(IngestionRunStatus.COMPLETED, now))
                    .contains(now.plus(Duration.ofDays(90)));
            assertThat(retentionPolicyService.calculateIngestionRunExpiry(IngestionRunStatus.FAILED, now))
                    .contains(now.plus(Duration.ofDays(90)));
            assertThat(retentionPolicyService.calculateIngestionRunExpiry(IngestionRunStatus.INTERRUPTED, now))
                    .contains(now.plus(Duration.ofDays(90)));

            // Missing finishedAt must return empty
            assertThat(retentionPolicyService.calculateIngestionRunExpiry(IngestionRunStatus.COMPLETED, null)).isEmpty();
        }

        @Test
        @DisplayName("IngestionTriggerRequest: PENDING, CLAIMED, RETYRING are null; COMPLETED, FAILED, CANCELLED expire in 30 days from completedAt; missing completedAt is null")
        void ingestionTriggerRetentionPolicy() {
            assertThat(retentionPolicyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_PENDING, now)).isEmpty();
            assertThat(retentionPolicyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_CLAIMED, now)).isEmpty();
            assertThat(retentionPolicyService.calculateIngestionTriggerExpiry("RETRYING", now)).isEmpty();

            assertThat(retentionPolicyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_COMPLETED, now))
                    .contains(now.plus(Duration.ofDays(30)));
            assertThat(retentionPolicyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_FAILED, now))
                    .contains(now.plus(Duration.ofDays(30)));
            assertThat(retentionPolicyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_CANCELLED, now))
                    .contains(now.plus(Duration.ofDays(30)));

            // Missing completedAt must return empty
            assertThat(retentionPolicyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_COMPLETED, null)).isEmpty();
        }

        @Test
        @DisplayName("AdminAuditEvent: expires in 365 days from creation")
        void adminAuditEventRetentionPolicy() {
            Optional<Instant> auditExpiry = retentionPolicyService.calculateAdminAuditExpiry(now);
            assertThat(auditExpiry).contains(now.plus(Duration.ofDays(365)));
        }

        @Test
        @DisplayName("Null timestamp handling produces Optional.empty() without exceptions across all entities")
        void nullTimestampHandling() {
            assertThat(retentionPolicyService.calculateNotificationUnreadExpiry(null)).isEmpty();
            assertThat(retentionPolicyService.calculateNotificationReadExpiry(null)).isEmpty();
            assertThat(retentionPolicyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.PUBLISHED, null)).isEmpty();
            assertThat(retentionPolicyService.calculateIngestionRunExpiry(IngestionRunStatus.COMPLETED, null)).isEmpty();
            assertThat(retentionPolicyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_COMPLETED, null)).isEmpty();
            assertThat(retentionPolicyService.calculateAdminAuditExpiry(null)).isEmpty();
        }
    }
}
