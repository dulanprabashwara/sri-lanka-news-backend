package lk.srilankannews.retention;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lk.srilankannews.ingestion.run.IngestionRunStatus;
import lk.srilankannews.ingestion.trigger.IngestionTriggerRequest;
import lk.srilankannews.notifications.NotificationEvent.EventStatus;
import org.springframework.stereotype.Service;

/**
 * Pure, side-effect-free policy service calculating retention expiration timestamps.
 * Does NOT perform any database, Redis, or system state mutations.
 */
@Service
public class RetentionPolicyService {

    private final RetentionProperties properties;

    public RetentionPolicyService(RetentionProperties properties) {
        this.properties = properties;
    }

    public RetentionProperties getProperties() {
        return properties;
    }

    /**
     * Calculates expiry for a read notification.
     * Retains read notifications for `news.retention.notifications.read-days` after `readAt`.
     */
    public Optional<Instant> calculateNotificationReadExpiry(Instant readAt) {
        if (readAt == null) {
            return Optional.empty();
        }
        return Optional.of(readAt.plus(properties.notifications().readDays(), ChronoUnit.DAYS));
    }

    /**
     * Calculates initial retention expiry for new unread notifications.
     * Retains unread notifications for a maximum of `news.retention.notifications.unread-max-days` after `createdAt`.
     */
    public Optional<Instant> calculateNotificationUnreadExpiry(Instant createdAt) {
        return calculateNotificationUnreadMaxExpiry(createdAt);
    }

    /**
     * Calculates maximum lifecycle expiry for unread notifications.
     * Retains unread notifications for a maximum of `news.retention.notifications.unread-max-days` after `createdAt`.
     */
    public Optional<Instant> calculateNotificationUnreadMaxExpiry(Instant createdAt) {
        if (createdAt == null) {
            return Optional.empty();
        }
        return Optional.of(createdAt.plus(properties.notifications().unreadMaxDays(), ChronoUnit.DAYS));
    }

    /**
     * Calculates expiry for outbox notification pipeline events based on terminal status.
     * Active states (PENDING, PROCESSING, RETRYING) never expire.
     * FAILED status requires a valid terminal timestamp; if absent, returns Optional.empty().
     */
    public Optional<Instant> calculateNotificationOutboxExpiry(EventStatus status, Instant terminalAt) {
        if (status == null || terminalAt == null) {
            return Optional.empty();
        }
        return switch (status) {
            case PUBLISHED, PROCESSED -> Optional.of(terminalAt.plus(properties.notificationOutbox().publishedDays(), ChronoUnit.DAYS));
            case FAILED -> Optional.of(terminalAt.plus(properties.notificationOutbox().failedDays(), ChronoUnit.DAYS));
            case PENDING, PROCESSING, RETRYING -> Optional.empty();
        };
    }

    /**
     * Calculates expiry for ingestion execution runs based on terminal status.
     * Active runs (RUNNING) never expire.
     */
    public Optional<Instant> calculateIngestionRunExpiry(IngestionRunStatus status, Instant finishedAt) {
        if (status == null || finishedAt == null) {
            return Optional.empty();
        }
        return switch (status) {
            case COMPLETED, FAILED, INTERRUPTED -> Optional.of(finishedAt.plus(properties.ingestionRuns().terminalDays(), ChronoUnit.DAYS));
            case RUNNING -> Optional.empty();
        };
    }

    /**
     * Calculates expiry for manual ingestion trigger requests.
     * Active/unprocessed requests (PENDING, CLAIMED) never expire.
     */
    public Optional<Instant> calculateIngestionTriggerExpiry(String status, Instant completedAt) {
        if (status == null || completedAt == null) {
            return Optional.empty();
        }
        return switch (status) {
            case IngestionTriggerRequest.STATUS_COMPLETED,
                 IngestionTriggerRequest.STATUS_FAILED,
                 IngestionTriggerRequest.STATUS_CANCELLED ->
                    Optional.of(completedAt.plus(properties.ingestionTriggers().processedDays(), ChronoUnit.DAYS));
            default -> Optional.empty();
        };
    }

    /**
     * Calculates expiry for administrative security audit logs.
     */
    public Optional<Instant> calculateAdminAuditExpiry(Instant createdAt) {
        if (createdAt == null) {
            return Optional.empty();
        }
        return Optional.of(createdAt.plus(properties.adminAudit().days(), ChronoUnit.DAYS));
    }
}
