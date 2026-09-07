package lk.srilankannews.retention;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class RetentionMongoStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionMongoStorageService.class);

    public static final Set<String> PERMANENT_COLLECTIONS = Set.of(
            "articles",
            "stories",
            "sources",
            "user_preferences",
            "notification_preferences",
            "user_bookmarks",
            "user_follows",
            "ingestion_source_settings");

    public static final Set<String> TTL_CONTROLLED_COLLECTIONS = Set.of(
            "notifications",
            "notification_events",
            "ingestion_runs",
            "ingestion_trigger_requests",
            "admin_audit_events");

    public static final String ANALYTICS_EVENTS_COLLECTION = "analytics_events";
    public static final String ANALYTICS_DAILY_VISITORS_COLLECTION = "analytics_daily_visitors";
    public static final String ANALYTICS_DAILY_METRICS_COLLECTION = "analytics_daily_metrics";

    private final MongoTemplate mongoTemplate;

    public RetentionMongoStorageService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public MongoStorageOverview getStorageOverview() {
        try {
            Document dbStats = mongoTemplate.getDb().runCommand(new Document("dbStats", 1));
            long dataSize = getLongOrDefault(dbStats, "dataSize", 0L);
            long storageSize = getLongOrDefault(dbStats, "storageSize", 0L);
            long indexSize = getLongOrDefault(dbStats, "indexSize", 0L);
            long totalIndexSize = getLongOrDefault(dbStats, "totalIndexSize", indexSize);
            long collectionCount = getLongOrDefault(dbStats, "collections", 0L);

            List<CollectionStorageMetric> collectionMetrics = new ArrayList<>();
            List<String> monitoredCollections = List.of(
                    "articles",
                    "stories",
                    "notifications",
                    "notification_events",
                    "ingestion_runs",
                    "ingestion_trigger_requests",
                    "admin_audit_events",
                    "analytics_events",
                    "analytics_daily_metrics",
                    "analytics_daily_visitors",
                    "sources",
                    "user_preferences",
                    "notification_preferences",
                    "user_bookmarks",
                    "user_follows",
                    "ingestion_source_settings");

            for (String collName : monitoredCollections) {
                if (!mongoTemplate.collectionExists(collName)) {
                    collectionMetrics.add(new CollectionStorageMetric(
                            collName, 0L, 0L, 0L, classifyCollection(collName), "COLLECTION ABSENT / SAFE"));
                    continue;
                }

                try {
                    Document collStats = mongoTemplate.getDb().runCommand(new Document("collStats", collName));
                    long docCount = getLongOrDefault(collStats, "count", 0L);
                    long size = getLongOrDefault(collStats, "size", 0L);
                    long collIndexSize = getLongOrDefault(collStats, "totalIndexSize", 0L);

                    collectionMetrics.add(new CollectionStorageMetric(
                            collName, docCount, size, collIndexSize, classifyCollection(collName), "PRESENT"));
                } catch (Exception e) {
                    LOGGER.warn("Failed to get collStats for coll={}", collName, e);
                    collectionMetrics.add(new CollectionStorageMetric(
                            collName, -1L, -1L, -1L, classifyCollection(collName), "STATS ERROR"));
                }
            }

            return new MongoStorageOverview(
                    true,
                    dataSize,
                    storageSize,
                    indexSize,
                    totalIndexSize,
                    collectionCount,
                    collectionMetrics);
        } catch (Exception e) {
            LOGGER.error("Failed to query Mongo dbStats", e);
            return new MongoStorageOverview(false, 0L, 0L, 0L, 0L, 0L, List.of());
        }
    }

    public List<TtlIndexHealthStatus> getTtlIndexHealth() {
        List<TtlIndexHealthStatus> healthList = new ArrayList<>();

        // 1. Retention TTL Collections
        for (String collName : TTL_CONTROLLED_COLLECTIONS) {
            if (!mongoTemplate.collectionExists(collName)) {
                healthList.add(new TtlIndexHealthStatus(
                        collName, "expiresAt", "HEALTHY", "COLLECTION ABSENT (NO INDEX REQUIRED)"));
                continue;
            }

            TtlIndexCheck check = checkCollectionTtlIndex(collName, "expiresAt", 0L);
            healthList.add(new TtlIndexHealthStatus(
                    collName, "expiresAt", check.status(), check.details()));
        }

        // 2. Fixed Analytics TTL Collection
        if (mongoTemplate.collectionExists(ANALYTICS_EVENTS_COLLECTION)) {
            TtlIndexCheck check = checkCollectionTtlIndex(ANALYTICS_EVENTS_COLLECTION, "receivedAt", 2592000L);
            healthList.add(new TtlIndexHealthStatus(
                    ANALYTICS_EVENTS_COLLECTION, "receivedAt", check.status(), check.details()));
        } else {
            healthList.add(new TtlIndexHealthStatus(
                    ANALYTICS_EVENTS_COLLECTION, "receivedAt", "HEALTHY", "COLLECTION ABSENT"));
        }

        // 3. Permanent Collections Verification
        for (String collName : PERMANENT_COLLECTIONS) {
            if (!mongoTemplate.collectionExists(collName)) {
                healthList.add(new TtlIndexHealthStatus(
                        collName, "NONE", "HEALTHY", "PERMANENT COLLECTION ABSENT / SAFE"));
                continue;
            }

            boolean hasTtl = false;
            for (Document indexDoc : mongoTemplate.getCollection(collName).listIndexes()) {
                if (indexDoc.containsKey("expireAfterSeconds")) {
                    hasTtl = true;
                    break;
                }
            }

            if (hasTtl) {
                healthList.add(new TtlIndexHealthStatus(
                        collName, "NONE", "UNEXPECTED", "UNEXPECTED TTL INDEX FOUND ON PERMANENT DATASET"));
            } else {
                healthList.add(new TtlIndexHealthStatus(
                        collName, "NONE", "HEALTHY", "NO TTL INDEX PRESENT (PERMANENT DATASET SAFE)"));
            }
        }

        return healthList;
    }

    public List<TtlDocumentLifecycleStatus> getTtlDocumentHealth(Instant now) {
        List<TtlDocumentLifecycleStatus> docHealthList = new ArrayList<>();
        Date nowDate = Date.from(now);

        for (String collName : TTL_CONTROLLED_COLLECTIONS) {
            if (!mongoTemplate.collectionExists(collName)) {
                docHealthList.add(new TtlDocumentLifecycleStatus(
                        collName, 0L, 0L, 0L, 0L, null, "COLLECTION ABSENT"));
                continue;
            }

            long total = mongoTemplate.count(new Query(), collName);
            long withExpiresAt = mongoTemplate.count(
                    Query.query(Criteria.where("expiresAt").exists(true).ne(null)), collName);
            long withoutExpiresAt = mongoTemplate.count(
                    Query.query(new Criteria().orOperator(
                            Criteria.where("expiresAt").exists(false),
                            Criteria.where("expiresAt").is(null))), collName);
            long expiredAwaitingCleanup = mongoTemplate.count(
                    Query.query(Criteria.where("expiresAt").lt(nowDate)), collName);

            Instant nextEarliestExpiresAt = null;
            Query earliestQuery = Query.query(Criteria.where("expiresAt").gt(nowDate))
                    .with(Sort.by(Sort.Direction.ASC, "expiresAt"))
                    .limit(1);
            Document earliestDoc = mongoTemplate.findOne(earliestQuery, Document.class, collName);
            if (earliestDoc != null && earliestDoc.get("expiresAt") instanceof Date dateVal) {
                nextEarliestExpiresAt = dateVal.toInstant();
            }

            String missingClassification = "NONE";
            if (withoutExpiresAt > 0) {
                if ("notifications".equals(collName)) {
                    missingClassification = "ACTIVE PROTECTED (UNREAD NOTIFICATIONS)";
                } else if ("notification_events".equals(collName)) {
                    missingClassification = "FAILED DEFERRED (FAILED EVENTS LACK CANONICAL TIMESTAMP)";
                } else {
                    missingClassification = "UNEXPECTED MISSING EXPIRESAT";
                }
            }

            docHealthList.add(new TtlDocumentLifecycleStatus(
                    collName,
                    total,
                    withExpiresAt,
                    withoutExpiresAt,
                    expiredAwaitingCleanup,
                    nextEarliestExpiresAt,
                    missingClassification));
        }

        return docHealthList;
    }

    private TtlIndexCheck checkCollectionTtlIndex(String collName, String expectedField, long expectedExpireAfterSecs) {
        try {
            boolean fieldIndexed = false;
            boolean durationMatches = false;
            Long actualExpire = null;

            for (Document indexDoc : mongoTemplate.getCollection(collName).listIndexes()) {
                Document keyDoc = (Document) indexDoc.get("key");
                if (keyDoc != null && keyDoc.containsKey(expectedField) && keyDoc.getInteger(expectedField, 0) == 1) {
                    fieldIndexed = true;
                    if (indexDoc.containsKey("expireAfterSeconds")) {
                        Object expireObj = indexDoc.get("expireAfterSeconds");
                        if (expireObj instanceof Number num) {
                            actualExpire = num.longValue();
                            if (actualExpire == expectedExpireAfterSecs) {
                                durationMatches = true;
                            }
                        }
                    }
                }
            }

            if (fieldIndexed && durationMatches) {
                return new TtlIndexCheck("HEALTHY", "TTL INDEX ON '" + expectedField + "' (expireAfterSeconds=" + expectedExpireAfterSecs + ") IS HEALTHY");
            } else if (fieldIndexed) {
                return new TtlIndexCheck("CONFLICTING", "TTL INDEX ON '" + expectedField + "' HAS CONFLICTING EXPIRE DURATION: " + actualExpire);
            } else {
                return new TtlIndexCheck("MISSING", "TTL INDEX ON '" + expectedField + "' IS MISSING");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to inspect Mongo index for coll={}", collName, e);
            return new TtlIndexCheck("UNEXPECTED", "INDEX INSPECTION ERROR: " + e.getMessage());
        }
    }

    private String classifyCollection(String collName) {
        if (PERMANENT_COLLECTIONS.contains(collName)) {
            return "PERMANENT";
        }
        if (TTL_CONTROLLED_COLLECTIONS.contains(collName)) {
            return "TTL CONTROLLED";
        }
        if (ANALYTICS_EVENTS_COLLECTION.equals(collName)) {
            return "EXISTING FIXED TTL";
        }
        if (ANALYTICS_DAILY_VISITORS_COLLECTION.equals(collName)) {
            return "RETENTION CANDIDATE";
        }
        return "OPERATIONAL / OTHER";
    }

    private long getLongOrDefault(Document doc, String key, long defaultValue) {
        if (doc != null && doc.containsKey(key)) {
            Object val = doc.get(key);
            if (val instanceof Number num) {
                return num.longValue();
            }
        }
        return defaultValue;
    }

    public record MongoStorageOverview(
            boolean available,
            long dataSize,
            long storageSize,
            long indexSize,
            long totalIndexSize,
            long collectionCount,
            List<CollectionStorageMetric> collections) {
    }

    public record CollectionStorageMetric(
            String collectionName,
            long documentCount,
            long storageSize,
            long indexSize,
            String retentionClassification,
            String status) {
    }

    public record TtlIndexHealthStatus(
            String collectionName,
            String field,
            String status,
            String details) {
    }

    public record TtlDocumentLifecycleStatus(
            String collectionName,
            long totalDocuments,
            long withExpiresAt,
            long withoutExpiresAt,
            long expiredAwaitingCleanup,
            Instant nextEarliestExpiresAt,
            String missingExpiryClassification) {
    }

    private record TtlIndexCheck(String status, String details) {
    }
}
