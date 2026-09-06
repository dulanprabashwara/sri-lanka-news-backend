package lk.srilankannews.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lk.srilankannews.admin.audit.AdminAuditEvent;
import lk.srilankannews.ingestion.run.IngestionRun;
import lk.srilankannews.ingestion.run.IngestionRunStatus;
import lk.srilankannews.ingestion.run.IngestionTriggerType;
import lk.srilankannews.ingestion.trigger.IngestionTriggerRequest;
import lk.srilankannews.notifications.Notification;
import lk.srilankannews.notifications.NotificationEvent;
import lk.srilankannews.notifications.NotificationEvent.EventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class RetentionBackfillServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private RetentionPolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new RetentionPolicyService(RetentionProperties.defaults());
    }

    @Test
    @DisplayName("DR4B — Backfill disabled by default performs zero operations")
    void testDisabledByDefault() {
        RetentionBackfillProperties defaultProps = RetentionBackfillProperties.defaults();
        RetentionBackfillService service = new RetentionBackfillService(mongoTemplate, policyService, defaultProps);

        List<RetentionBackfillResult> results = service.performBackfill();
        assertThat(results).isEmpty();
        verify(mongoTemplate, never()).find(any(), any());
    }

    @Test
    @DisplayName("DR4B — Dry run mode (enabled=true, apply=false) calculates candidates without writing expiresAt")
    void testDryRunMode() {
        Instant now = Instant.now().minus(10, ChronoUnit.DAYS);
        Notification notif = new Notification(
                "n1", "u1", Notification.NotificationType.STORY_ACTIVITY, "s1", "a1", "src1", "src-slug", "Source",
                List.of(), "Title", "Msg", "/path", "1.0", "dedupe1", now, null, null, null
        );

        when(mongoTemplate.find(any(Query.class), eq(Notification.class)))
                .thenReturn(List.of(notif)) // 1st call in safety check
                .thenReturn(List.of(notif)) // 1st call in backfillNotifications
                .thenReturn(List.of());     // 2nd call loop break

        when(mongoTemplate.find(any(Query.class), eq(NotificationEvent.class))).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(IngestionRun.class))).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(IngestionTriggerRequest.class))).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(AdminAuditEvent.class))).thenReturn(List.of());

        RetentionBackfillProperties dryRunProps = new RetentionBackfillProperties(true, false, 100);
        RetentionBackfillService service = new RetentionBackfillService(mongoTemplate, policyService, dryRunProps);

        List<RetentionBackfillResult> results = service.performBackfill();
        assertThat(results).hasSize(5);

        RetentionBackfillResult notifRes = results.stream()
                .filter(r -> r.collectionName().equals("notifications"))
                .findFirst().orElseThrow();

        assertThat(notifRes.examinedCount()).isEqualTo(1);
        assertThat(notifRes.eligibleCount()).isEqualTo(1);
        assertThat(notifRes.updatedCount()).isEqualTo(0);

        verify(mongoTemplate, never()).updateFirst(any(), any(), eq("notifications"));
    }

    @Test
    @DisplayName("DR4B — Apply mode updates eligible historical records correctly")
    void testApplyMode() {
        Instant now = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant readAt = now.plus(1, ChronoUnit.DAYS);

        Notification notif = new Notification(
                "n1", "u1", Notification.NotificationType.STORY_ACTIVITY, "s1", "a1", "src1", "src-slug", "Source",
                List.of(), "Title", "Msg", "/path", "1.0", "dedupe1", now, readAt, null, null
        );
        NotificationEvent event = new NotificationEvent(
                "ne1", "a1", "s1", "src1", now, "1.0", EventStatus.PUBLISHED, 1, null, readAt, null, null, null
        );
        IngestionRun run = new IngestionRun(
                "run1", "src1", "src-slug", IngestionTriggerType.SCHEDULED, IngestionRunStatus.COMPLETED,
                now, now, readAt, "worker1", null, 10, 10, 10, 0, null, null, now, now, null
        );
        IngestionTriggerRequest trigger = new IngestionTriggerRequest(
                "trig1", "src1", "src-slug", "admin1", now, IngestionTriggerRequest.STATUS_COMPLETED,
                1, null, now, "worker1", "run1", readAt, null
        );
        AdminAuditEvent audit = new AdminAuditEvent("audit1", "admin1", "LOGIN", "src1", null, now, null);

        // Safety check returns these records
        when(mongoTemplate.find(any(Query.class), eq(Notification.class)))
                .thenReturn(List.of(notif))
                .thenReturn(List.of(notif))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(NotificationEvent.class)))
                .thenReturn(List.of(event))
                .thenReturn(List.of(event))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(IngestionRun.class)))
                .thenReturn(List.of(run))
                .thenReturn(List.of(run))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(IngestionTriggerRequest.class)))
                .thenReturn(List.of(trigger))
                .thenReturn(List.of(trigger))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(AdminAuditEvent.class)))
                .thenReturn(List.of(audit))
                .thenReturn(List.of(audit))
                .thenReturn(List.of());

        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), any(String.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        RetentionBackfillProperties applyProps = new RetentionBackfillProperties(true, true, 100);
        RetentionBackfillService service = new RetentionBackfillService(mongoTemplate, policyService, applyProps);

        List<RetentionBackfillResult> results = service.performBackfill();
        assertThat(results).hasSize(5);

        for (RetentionBackfillResult res : results) {
            assertThat(res.examinedCount()).isEqualTo(1);
            assertThat(res.eligibleCount()).isEqualTo(1);
            assertThat(res.updatedCount()).isEqualTo(1);
        }

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq("notifications"));
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq("notification_events"));
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq("ingestion_runs"));
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq("ingestion_trigger_requests"));
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq("admin_audit_events"));
    }

    @Test
    @DisplayName("DR4B — Idempotency: Existing expiresAt query returns empty candidate list")
    void testIdempotency() {
        when(mongoTemplate.find(any(Query.class), eq(Notification.class))).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(NotificationEvent.class))).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(IngestionRun.class))).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(IngestionTriggerRequest.class))).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(AdminAuditEvent.class))).thenReturn(List.of());

        RetentionBackfillProperties applyProps = new RetentionBackfillProperties(true, true, 100);
        RetentionBackfillService service = new RetentionBackfillService(mongoTemplate, policyService, applyProps);

        List<RetentionBackfillResult> results = service.performBackfill();

        for (RetentionBackfillResult res : results) {
            assertThat(res.examinedCount()).isEqualTo(0);
            assertThat(res.updatedCount()).isEqualTo(0);
        }

        verify(mongoTemplate, never()).updateFirst(any(), any(), any(String.class));
    }

    @Test
    @DisplayName("DR4B — Pre-Apply Immediate Expiry Safety Gate throws exception if immediate candidates exist")
    void testSafetyGateFailure() {
        Instant oldDate = Instant.now().minus(400, ChronoUnit.DAYS); // Expired unread notification (365 days max)
        Notification expiredNotif = new Notification(
                "n1", "u1", Notification.NotificationType.STORY_ACTIVITY, "s1", "a1", "src1", "src-slug", "Source",
                List.of(), "Title", "Msg", "/path", "1.0", "dedupe1", oldDate, null, null, null
        );

        when(mongoTemplate.find(any(Query.class), eq(Notification.class))).thenReturn(List.of(expiredNotif));
        when(mongoTemplate.find(any(Query.class), eq(NotificationEvent.class))).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(IngestionRun.class))).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(IngestionTriggerRequest.class))).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(AdminAuditEvent.class))).thenReturn(List.of());

        RetentionBackfillProperties applyProps = new RetentionBackfillProperties(true, true, 100);
        RetentionBackfillService service = new RetentionBackfillService(mongoTemplate, policyService, applyProps);

        assertThatThrownBy(service::performBackfill)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IMMEDIATE EXPIRY SAFETY GATE FAILED");

        verify(mongoTemplate, never()).updateFirst(any(), any(), any(String.class));
    }
}
