package lk.srilankannews.retention;

import lk.srilankannews.ingestion.run.IngestionRunStatus;
import lk.srilankannews.ingestion.trigger.IngestionTriggerRequest;
import lk.srilankannews.notifications.NotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalRetentionDryRunUnitTest {

    private RetentionPolicyService policyService;
    private Instant now;

    @BeforeEach
    void setUp() {
        policyService = new RetentionPolicyService(RetentionProperties.defaults());
        now = Instant.parse("2026-09-06T12:00:00Z");
    }

    @Test
    @DisplayName("Notification Candidate Expiry: Read (readAt + 180d) and Unread (createdAt + 365d)")
    void notificationCandidateExpiry() {
        Instant createdAt = now.minus(Duration.ofDays(400)); // 400 days old
        Instant readAt = now.minus(Duration.ofDays(200));    // 200 days old

        // Unread candidate expiry
        Optional<Instant> unreadExpiry = policyService.calculateNotificationUnreadExpiry(createdAt);
        assertThat(unreadExpiry).contains(createdAt.plus(Duration.ofDays(365)));
        assertThat(unreadExpiry.get()).isBefore(now); // Immediate expiry candidate!

        // Read candidate expiry
        Optional<Instant> readExpiry = policyService.calculateNotificationReadExpiry(readAt);
        assertThat(readExpiry).contains(readAt.plus(Duration.ofDays(180)));
        assertThat(readExpiry.get()).isBefore(now); // Immediate expiry candidate!
    }

    @Test
    @DisplayName("NotificationEvent Candidate Expiry: PUBLISHED/PROCESSED expire in 30d; FAILED is deferred (null)")
    void notificationEventCandidateExpiry() {
        Instant publishedAt = now.minus(Duration.ofDays(10));
        Instant processedAt = now.minus(Duration.ofDays(40));

        // PUBLISHED
        Optional<Instant> pubExpiry = policyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.PUBLISHED, publishedAt);
        assertThat(pubExpiry).contains(publishedAt.plus(Duration.ofDays(30)));
        assertThat(pubExpiry.get()).isAfter(now); // Expire in future (20 days from now)

        // PROCESSED
        Optional<Instant> procExpiry = policyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.PROCESSED, processedAt);
        assertThat(procExpiry).contains(processedAt.plus(Duration.ofDays(30)));
        assertThat(procExpiry.get()).isBefore(now); // Immediate expiry candidate!

        // FAILED without terminal timestamp -> DEFERRED
        Optional<Instant> failedExpiry = policyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.FAILED, null);
        assertThat(failedExpiry).isEmpty();

        // Active state -> Protected (null)
        Optional<Instant> pendingExpiry = policyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.PENDING, now);
        assertThat(pendingExpiry).isEmpty();
    }

    @Test
    @DisplayName("IngestionRun Candidate Expiry: Terminal states expire in 90d from finishedAt; missing finishedAt is deferred")
    void ingestionRunCandidateExpiry() {
        Instant finishedAt = now.minus(Duration.ofDays(100));

        Optional<Instant> runExpiry = policyService.calculateIngestionRunExpiry(IngestionRunStatus.COMPLETED, finishedAt);
        assertThat(runExpiry).contains(finishedAt.plus(Duration.ofDays(90)));
        assertThat(runExpiry.get()).isBefore(now); // Immediate expiry candidate!

        // Missing finishedAt -> DEFERRED
        Optional<Instant> missingFinishedExpiry = policyService.calculateIngestionRunExpiry(IngestionRunStatus.COMPLETED, null);
        assertThat(missingFinishedExpiry).isEmpty();

        // Active state -> Protected (null)
        Optional<Instant> activeExpiry = policyService.calculateIngestionRunExpiry(IngestionRunStatus.RUNNING, now);
        assertThat(activeExpiry).isEmpty();
    }

    @Test
    @DisplayName("IngestionTriggerRequest Candidate Expiry: Terminal states expire in 30d from completedAt; missing completedAt is deferred")
    void ingestionTriggerCandidateExpiry() {
        Instant completedAt = now.minus(Duration.ofDays(15));

        Optional<Instant> triggerExpiry = policyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_COMPLETED, completedAt);
        assertThat(triggerExpiry).contains(completedAt.plus(Duration.ofDays(30)));
        assertThat(triggerExpiry.get()).isAfter(now); // Expire in 15 days

        // Missing completedAt -> DEFERRED
        Optional<Instant> missingCompletedExpiry = policyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_COMPLETED, null);
        assertThat(missingCompletedExpiry).isEmpty();

        // Active state -> Protected (null)
        Optional<Instant> activeExpiry = policyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_PENDING, now);
        assertThat(activeExpiry).isEmpty();
    }

    @Test
    @DisplayName("AdminAuditEvent Candidate Expiry: createdAt + 365d; missing createdAt is deferred")
    void adminAuditEventCandidateExpiry() {
        Instant createdAt = now.minus(Duration.ofDays(400));

        Optional<Instant> auditExpiry = policyService.calculateAdminAuditExpiry(createdAt);
        assertThat(auditExpiry).contains(createdAt.plus(Duration.ofDays(365)));
        assertThat(auditExpiry.get()).isBefore(now); // Immediate expiry candidate!

        assertThat(policyService.calculateAdminAuditExpiry(null)).isEmpty();
    }
}
