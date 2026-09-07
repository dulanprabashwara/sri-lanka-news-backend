package lk.srilankannews.retention;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class RetentionTtlActivationService {

    private static final Logger log = LoggerFactory.getLogger(RetentionTtlActivationService.class);

    public record TargetCollectionSpec(
            String collectionName,
            String indexName
    ) {}

    public static final List<TargetCollectionSpec> STAGED_COLLECTIONS = List.of(
            new TargetCollectionSpec("admin_audit_events", "ttl_admin_audit_events_expiresAt"),
            new TargetCollectionSpec("notifications", "ttl_notifications_expiresAt"),
            new TargetCollectionSpec("notification_events", "ttl_notification_events_expiresAt"),
            new TargetCollectionSpec("ingestion_trigger_requests", "ttl_ingestion_triggers_expiresAt"),
            new TargetCollectionSpec("ingestion_runs", "ttl_ingestion_runs_expiresAt")
    );

    private final MongoTemplate mongoTemplate;
    private final RetentionTtlActivationProperties properties;

    public RetentionTtlActivationService(MongoTemplate mongoTemplate, RetentionTtlActivationProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    public List<RetentionTtlActivationResult> performActivation() {
        if (!properties.enabled()) {
            log.info("Retention TTL Activation is disabled (news.retention.ttl-activation.enabled=false).");
            return Collections.emptyList();
        }

        log.info("Starting Retention TTL Activation (apply={})", properties.apply());
        List<RetentionTtlActivationResult> results = new ArrayList<>();
        Instant now = Instant.now();

        for (TargetCollectionSpec spec : STAGED_COLLECTIONS) {
            String colName = spec.collectionName();
            String indexName = spec.indexName();

            // 1. Pre-activation counts
            long preTotal = mongoTemplate.count(new Query(), colName);
            long preWithExpires = mongoTemplate.count(new Query(Criteria.where("expiresAt").ne(null)), colName);
            long preImmediateExpired = mongoTemplate.count(new Query(Criteria.where("expiresAt").lte(now)), colName);

            // 2. Safety Gate: Immediate expired count must be 0
            if (preImmediateExpired > 0) {
                String errMsg = String.format("SAFETY GATE VIOLATION: Collection '%s' has %d immediate expired records (expiresAt <= NOW). Activation halted.",
                        colName, preImmediateExpired);
                log.error(errMsg);
                results.add(RetentionTtlActivationResult.failed(colName, indexName, "expiresAt", 0, preTotal, preWithExpires, preImmediateExpired, errMsg));
                throw new IllegalStateException(errMsg);
            }

            // 3. Inspect existing indexes
            List<Document> existingIndexes = mongoTemplate.getCollection(colName).listIndexes().into(new ArrayList<>());
            Document existingExpiresIndex = findExpiresAtIndex(existingIndexes, indexName);

            if (existingExpiresIndex != null) {
                Number expireAfterSeconds = (Number) existingExpiresIndex.get("expireAfterSeconds");
                if (expireAfterSeconds != null && expireAfterSeconds.longValue() == 0L) {
                    log.info("Collection '{}' already has matching TTL index '{}' (expireAfterSeconds=0). Skipping creation.", colName, indexName);
                    results.add(RetentionTtlActivationResult.existingMatch(colName, indexName, "expiresAt", 0, preTotal, preWithExpires, preImmediateExpired, preTotal));
                    continue;
                } else {
                    String conflictMsg = String.format("CONFLICT DETECTED: Collection '%s' has an existing index on expiresAt with non-matching TTL config: %s",
                            colName, existingExpiresIndex.toJson());
                    log.error(conflictMsg);
                    results.add(RetentionTtlActivationResult.failed(colName, indexName, "expiresAt", 0, preTotal, preWithExpires, preImmediateExpired, conflictMsg));
                    throw new IllegalStateException(conflictMsg);
                }
            }

            // 4. Preview mode (apply = false)
            if (!properties.apply()) {
                log.info("PREVIEW MODE: Would create TTL index '{}' on '{}.expiresAt' (expireAfterSeconds=0).", indexName, colName);
                results.add(RetentionTtlActivationResult.skipped(colName, indexName, "expiresAt", 0, preTotal, preWithExpires, preImmediateExpired));
                continue;
            }

            // 5. Apply mode (apply = true) -> Create single-field TTL index
            try {
                log.info("Creating TTL index '{}' on '{}.expiresAt' (expireAfterSeconds=0)...", indexName, colName);
                Index indexSpec = new Index().on("expiresAt", Sort.Direction.ASC).expire(0).named(indexName);
                mongoTemplate.indexOps(colName).ensureIndex(indexSpec);

                long postTotal = mongoTemplate.count(new Query(), colName);
                log.info("Successfully created TTL index '{}' on collection '{}'. Post count: {}", indexName, colName, postTotal);

                results.add(RetentionTtlActivationResult.created(colName, indexName, "expiresAt", 0, preTotal, preWithExpires, preImmediateExpired, postTotal));
            } catch (Exception e) {
                String errorMsg = String.format("FAILED to create TTL index '%s' on collection '%s': %s", indexName, colName, e.getMessage());
                log.error(errorMsg, e);
                results.add(RetentionTtlActivationResult.failed(colName, indexName, "expiresAt", 0, preTotal, preWithExpires, preImmediateExpired, errorMsg));
                throw new IllegalStateException(errorMsg, e);
            }
        }

        return results;
    }

    private Document findExpiresAtIndex(List<Document> indexes, String expectedIndexName) {
        for (Document idx : indexes) {
            String name = idx.getString("name");
            if (expectedIndexName.equals(name)) {
                return idx;
            }
            Document key = (Document) idx.get("key");
            if (key != null && key.containsKey("expiresAt")) {
                return idx;
            }
        }
        return null;
    }
}
