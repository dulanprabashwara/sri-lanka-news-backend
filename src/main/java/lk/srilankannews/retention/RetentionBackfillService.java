package lk.srilankannews.retention;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.admin.audit.AdminAuditEvent;
import lk.srilankannews.ingestion.run.IngestionRun;
import lk.srilankannews.ingestion.run.IngestionRunStatus;
import lk.srilankannews.ingestion.trigger.IngestionTriggerRequest;
import lk.srilankannews.notifications.Notification;
import lk.srilankannews.notifications.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Service for controlled historical retention metadata backfill.
 * Backfills missing `expiresAt` fields into historical MongoDB records.
 * Side-effect free unless explicit opt-in properties (`enabled=true`, `apply=true`) are present.
 */
@Service
public class RetentionBackfillService {

    private static final Logger log = LoggerFactory.getLogger(RetentionBackfillService.class);

    private final MongoTemplate mongoTemplate;
    private final RetentionPolicyService policyService;
    private final RetentionBackfillProperties backfillProperties;

    public RetentionBackfillService(
            MongoTemplate mongoTemplate,
            RetentionPolicyService policyService,
            RetentionBackfillProperties backfillProperties
    ) {
        this.mongoTemplate = mongoTemplate;
        this.policyService = policyService;
        this.backfillProperties = backfillProperties;
    }

    public List<RetentionBackfillResult> performBackfill() {
        if (!backfillProperties.enabled()) {
            log.info("Historical retention backfill is DISABLED (news.retention.backfill.enabled=false). Skipping.");
            return List.of();
        }

        boolean apply = backfillProperties.apply();
        log.info("Historical retention backfill starting. Mode: {}, Batch Size: {}",
                apply ? "APPLY (WRITES ENABLED)" : "DRY RUN (READ ONLY)", backfillProperties.batchSize());

        // Pre-Apply Immediate Expiry Safety Gate
        long immediateCandidates = checkImmediateExpiryCandidates();
        if (apply && immediateCandidates > 0) {
            throw new IllegalStateException("IMMEDIATE EXPIRY SAFETY GATE FAILED: Found "
                    + immediateCandidates + " candidate records that would expire immediately!");
        }

        List<RetentionBackfillResult> results = new ArrayList<>();
        results.add(backfillNotifications(apply));
        results.add(backfillNotificationEvents(apply));
        results.add(backfillIngestionRuns(apply));
        results.add(backfillIngestionTriggers(apply));
        results.add(backfillAdminAuditEvents(apply));

        log.info("Historical retention backfill finished. Results summary: {}", results);
        return results;
    }

    public long checkImmediateExpiryCandidates() {
        Instant now = Instant.now();
        long immediateCount = 0;

        // 1. Notifications
        for (Notification doc : mongoTemplate.find(Query.query(Criteria.where("expiresAt").is(null)), Notification.class)) {
            Optional<Instant> exp = doc.readAt() != null
                    ? policyService.calculateNotificationReadExpiry(doc.readAt())
                    : policyService.calculateNotificationUnreadExpiry(doc.createdAt());
            if (exp.isPresent() && !exp.get().isAfter(now)) immediateCount++;
        }

        // 2. Notification Events
        for (NotificationEvent doc : mongoTemplate.find(Query.query(Criteria.where("expiresAt").is(null)), NotificationEvent.class)) {
            Instant timestamp = doc.status() == NotificationEvent.EventStatus.PUBLISHED ? doc.publishedAt()
                    : doc.status() == NotificationEvent.EventStatus.PROCESSED ? doc.processedAt() : null;
            Optional<Instant> exp = policyService.calculateNotificationOutboxExpiry(doc.status(), timestamp);
            if (exp.isPresent() && !exp.get().isAfter(now)) immediateCount++;
        }

        // 3. Ingestion Runs
        for (IngestionRun doc : mongoTemplate.find(Query.query(Criteria.where("expiresAt").is(null)), IngestionRun.class)) {
            Optional<Instant> exp = policyService.calculateIngestionRunExpiry(doc.status(), doc.finishedAt());
            if (exp.isPresent() && !exp.get().isAfter(now)) immediateCount++;
        }

        // 4. Ingestion Triggers
        for (IngestionTriggerRequest doc : mongoTemplate.find(Query.query(Criteria.where("expiresAt").is(null)), IngestionTriggerRequest.class)) {
            Optional<Instant> exp = policyService.calculateIngestionTriggerExpiry(doc.status(), doc.completedAt());
            if (exp.isPresent() && !exp.get().isAfter(now)) immediateCount++;
        }

        // 5. Admin Audit Events
        for (AdminAuditEvent doc : mongoTemplate.find(Query.query(Criteria.where("expiresAt").is(null)), AdminAuditEvent.class)) {
            Optional<Instant> exp = policyService.calculateAdminAuditExpiry(doc.createdAt());
            if (exp.isPresent() && !exp.get().isAfter(now)) immediateCount++;
        }

        return immediateCount;
    }

    public RetentionBackfillResult backfillNotifications(boolean apply) {
        String collectionName = "notifications";
        long examined = 0, eligible = 0, updated = 0, skipped = 0, deferred = 0, errors = 0, immediate = 0;
        Instant now = Instant.now();

        Query query = Query.query(Criteria.where("expiresAt").is(null))
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .limit(backfillProperties.batchSize());

        List<Notification> batch;
        while (!(batch = mongoTemplate.find(query, Notification.class)).isEmpty()) {
            for (Notification doc : batch) {
                examined++;
                Optional<Instant> expOpt = doc.readAt() != null
                        ? policyService.calculateNotificationReadExpiry(doc.readAt())
                        : policyService.calculateNotificationUnreadExpiry(doc.createdAt());

                if (expOpt.isEmpty()) {
                    deferred++;
                    continue;
                }

                Instant expiresAt = expOpt.get();
                if (!expiresAt.isAfter(now)) immediate++;
                eligible++;

                if (apply) {
                    Query updateQuery = Query.query(Criteria.where("_id").is(doc.id())
                            .and("expiresAt").is(null));
                    var updateResult = mongoTemplate.updateFirst(updateQuery, Update.update("expiresAt", expiresAt), collectionName);
                    if (updateResult.getModifiedCount() > 0) {
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            }

            if (batch.size() < backfillProperties.batchSize() || !apply) {
                break;
            }
        }

        return new RetentionBackfillResult(collectionName, examined, eligible, updated, skipped, deferred, errors, immediate);
    }

    public RetentionBackfillResult backfillNotificationEvents(boolean apply) {
        String collectionName = "notification_events";
        long examined = 0, eligible = 0, updated = 0, skipped = 0, deferred = 0, errors = 0, immediate = 0;
        Instant now = Instant.now();

        Query query = Query.query(Criteria.where("expiresAt").is(null))
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .limit(backfillProperties.batchSize());

        List<NotificationEvent> batch;
        while (!(batch = mongoTemplate.find(query, NotificationEvent.class)).isEmpty()) {
            for (NotificationEvent doc : batch) {
                examined++;
                NotificationEvent.EventStatus st = doc.status();

                if (st == NotificationEvent.EventStatus.PENDING
                        || st == NotificationEvent.EventStatus.PROCESSING
                        || st == NotificationEvent.EventStatus.RETRYING) {
                    skipped++; // Active protected
                    continue;
                }

                Instant terminalAt = st == NotificationEvent.EventStatus.PUBLISHED ? doc.publishedAt()
                        : st == NotificationEvent.EventStatus.PROCESSED ? doc.processedAt() : null;

                Optional<Instant> expOpt = policyService.calculateNotificationOutboxExpiry(st, terminalAt);
                if (expOpt.isEmpty()) {
                    deferred++; // FAILED status or missing terminal timestamp
                    continue;
                }

                Instant expiresAt = expOpt.get();
                if (!expiresAt.isAfter(now)) immediate++;
                eligible++;

                if (apply) {
                    Query updateQuery = Query.query(Criteria.where("_id").is(doc.id())
                            .and("expiresAt").is(null)
                            .and("status").is(st));
                    var updateResult = mongoTemplate.updateFirst(updateQuery, Update.update("expiresAt", expiresAt), collectionName);
                    if (updateResult.getModifiedCount() > 0) {
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            }

            if (batch.size() < backfillProperties.batchSize() || !apply) {
                break;
            }
        }

        return new RetentionBackfillResult(collectionName, examined, eligible, updated, skipped, deferred, errors, immediate);
    }

    public RetentionBackfillResult backfillIngestionRuns(boolean apply) {
        String collectionName = "ingestion_runs";
        long examined = 0, eligible = 0, updated = 0, skipped = 0, deferred = 0, errors = 0, immediate = 0;
        Instant now = Instant.now();

        Query query = Query.query(Criteria.where("expiresAt").is(null))
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .limit(backfillProperties.batchSize());

        List<IngestionRun> batch;
        while (!(batch = mongoTemplate.find(query, IngestionRun.class)).isEmpty()) {
            for (IngestionRun doc : batch) {
                examined++;
                IngestionRunStatus st = doc.status();

                if (st == IngestionRunStatus.RUNNING) {
                    skipped++; // Active protected
                    continue;
                }

                Optional<Instant> expOpt = policyService.calculateIngestionRunExpiry(st, doc.finishedAt());
                if (expOpt.isEmpty()) {
                    deferred++; // Terminal missing finishedAt
                    continue;
                }

                Instant expiresAt = expOpt.get();
                if (!expiresAt.isAfter(now)) immediate++;
                eligible++;

                if (apply) {
                    Query updateQuery = Query.query(Criteria.where("_id").is(doc.id())
                            .and("expiresAt").is(null)
                            .and("status").is(st));
                    var updateResult = mongoTemplate.updateFirst(updateQuery, Update.update("expiresAt", expiresAt), collectionName);
                    if (updateResult.getModifiedCount() > 0) {
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            }

            if (batch.size() < backfillProperties.batchSize() || !apply) {
                break;
            }
        }

        return new RetentionBackfillResult(collectionName, examined, eligible, updated, skipped, deferred, errors, immediate);
    }

    public RetentionBackfillResult backfillIngestionTriggers(boolean apply) {
        String collectionName = "ingestion_trigger_requests";
        long examined = 0, eligible = 0, updated = 0, skipped = 0, deferred = 0, errors = 0, immediate = 0;
        Instant now = Instant.now();

        Query query = Query.query(Criteria.where("expiresAt").is(null))
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .limit(backfillProperties.batchSize());

        List<IngestionTriggerRequest> batch;
        while (!(batch = mongoTemplate.find(query, IngestionTriggerRequest.class)).isEmpty()) {
            for (IngestionTriggerRequest doc : batch) {
                examined++;
                String st = doc.status();

                if (IngestionTriggerRequest.STATUS_PENDING.equals(st)
                        || IngestionTriggerRequest.STATUS_CLAIMED.equals(st)) {
                    skipped++; // Active protected
                    continue;
                }

                Optional<Instant> expOpt = policyService.calculateIngestionTriggerExpiry(st, doc.completedAt());
                if (expOpt.isEmpty()) {
                    deferred++; // Terminal missing completedAt
                    continue;
                }

                Instant expiresAt = expOpt.get();
                if (!expiresAt.isAfter(now)) immediate++;
                eligible++;

                if (apply) {
                    Query updateQuery = Query.query(Criteria.where("_id").is(doc.id())
                            .and("expiresAt").is(null)
                            .and("status").is(st));
                    var updateResult = mongoTemplate.updateFirst(updateQuery, Update.update("expiresAt", expiresAt), collectionName);
                    if (updateResult.getModifiedCount() > 0) {
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            }

            if (batch.size() < backfillProperties.batchSize() || !apply) {
                break;
            }
        }

        return new RetentionBackfillResult(collectionName, examined, eligible, updated, skipped, deferred, errors, immediate);
    }

    public RetentionBackfillResult backfillAdminAuditEvents(boolean apply) {
        String collectionName = "admin_audit_events";
        long examined = 0, eligible = 0, updated = 0, skipped = 0, deferred = 0, errors = 0, immediate = 0;
        Instant now = Instant.now();

        Query query = Query.query(Criteria.where("expiresAt").is(null))
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .limit(backfillProperties.batchSize());

        List<AdminAuditEvent> batch;
        while (!(batch = mongoTemplate.find(query, AdminAuditEvent.class)).isEmpty()) {
            for (AdminAuditEvent doc : batch) {
                examined++;

                Optional<Instant> expOpt = policyService.calculateAdminAuditExpiry(doc.createdAt());
                if (expOpt.isEmpty()) {
                    deferred++; // Missing createdAt
                    continue;
                }

                Instant expiresAt = expOpt.get();
                if (!expiresAt.isAfter(now)) immediate++;
                eligible++;

                if (apply) {
                    Query updateQuery = Query.query(Criteria.where("_id").is(doc.id())
                            .and("expiresAt").is(null));
                    var updateResult = mongoTemplate.updateFirst(updateQuery, Update.update("expiresAt", expiresAt), collectionName);
                    if (updateResult.getModifiedCount() > 0) {
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            }

            if (batch.size() < backfillProperties.batchSize() || !apply) {
                break;
            }
        }

        return new RetentionBackfillResult(collectionName, examined, eligible, updated, skipped, deferred, errors, immediate);
    }
}
