package lk.srilankannews.retention;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroup;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroups;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RetentionRedisHealthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionRedisHealthService.class);

    private final StringRedisTemplate redis;
    private final RetentionRedisProperties properties;
    private final RedisStreamRetentionService retentionService;
    private final Clock clock;

    public RetentionRedisHealthService(
            StringRedisTemplate redis,
            RetentionRedisProperties properties,
            RedisStreamRetentionService retentionService,
            Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.retentionService = retentionService;
        this.clock = clock;
    }

    public RedisMemoryOverview getMemoryOverview() {
        try {
            Properties info = redis.execute((RedisCallback<Properties>) connection ->
                    connection.serverCommands().info("memory"));

            if (info == null || info.isEmpty()) {
                return new RedisMemoryOverview(false, -1L, "UNAVAILABLE", -1L, "UNAVAILABLE", -1L, "UNAVAILABLE", "UNKNOWN");
            }

            long usedMemory = parseLong(info.getProperty("used_memory"), -1L);
            String usedMemoryHuman = info.getProperty("used_memory_human", "UNAVAILABLE");
            long usedMemoryPeak = parseLong(info.getProperty("used_memory_peak"), -1L);
            String usedMemoryPeakHuman = info.getProperty("used_memory_peak_human", "UNAVAILABLE");
            long maxmemory = parseLong(info.getProperty("maxmemory"), -1L);
            String maxmemoryHuman = info.getProperty("maxmemory_human", "UNAVAILABLE");
            String maxmemoryPolicy = info.getProperty("maxmemory_policy", "UNKNOWN");

            return new RedisMemoryOverview(
                    true,
                    usedMemory,
                    usedMemoryHuman,
                    usedMemoryPeak,
                    usedMemoryPeakHuman,
                    maxmemory,
                    maxmemoryHuman,
                    maxmemoryPolicy);

        } catch (Exception e) {
            LOGGER.warn("Failed to retrieve Redis memory metrics", e);
            return new RedisMemoryOverview(false, -1L, "UNAVAILABLE", -1L, "UNAVAILABLE", -1L, "UNAVAILABLE", "UNKNOWN");
        }
    }

    public List<StreamHealthMetrics> getStreamHealthOverview(Instant now) {
        List<String> streams = List.of(
                RedisStreamRetentionService.DEFAULT_ARTICLE_STREAM,
                RedisStreamRetentionService.DEFAULT_NOTIFICATION_STREAM,
                RedisStreamRetentionService.DEFAULT_DLQ_STREAM);

        List<StreamHealthMetrics> metricsList = new ArrayList<>();

        for (String streamKey : streams) {
            try {
                Boolean exists = redis.hasKey(streamKey);
                if (Boolean.FALSE.equals(exists)) {
                    metricsList.add(new StreamHealthMetrics(
                            streamKey, 0L, "N/A", null, "N/A", null, List.of(), "STREAM ABSENT / EMPTY", true));
                    continue;
                }

                Long xlen = redis.opsForStream().size(streamKey);
                long size = xlen != null ? xlen : 0L;

                String firstId = retentionService.getFirstStreamEntryId(streamKey);
                String lastId = retentionService.getLastStreamEntryId(streamKey);

                Instant firstTime = parseStreamTimestamp(firstId);
                Instant lastTime = parseStreamTimestamp(lastId);

                Duration oldestAge = firstTime != null ? Duration.between(firstTime, now) : null;
                Duration newestAge = lastTime != null ? Duration.between(lastTime, now) : null;

                List<ConsumerGroupMetrics> groupMetrics = new ArrayList<>();
                boolean isDlq = streamKey.toLowerCase().contains("dlq");

                if (!isDlq) {
                    XInfoGroups groups = redis.opsForStream().groups(streamKey);
                    if (groups != null) {
                        for (XInfoGroup g : groups) {
                            String groupName = g.groupName();
                            long consumerCount = g.consumerCount();

                            long pCount = 0;
                            String minPendingId = null;
                            Duration oldestPendingAge = null;

                            try {
                                PendingMessagesSummary pendingSummary = redis.opsForStream().pending(streamKey, groupName);
                                if (pendingSummary != null) {
                                    pCount = pendingSummary.getTotalPendingMessages();
                                    if (pCount > 0 && pendingSummary.minRecordId() != null) {
                                        minPendingId = pendingSummary.minRecordId().getValue();
                                        Instant minPendingTime = parseStreamTimestamp(minPendingId);
                                        if (minPendingTime != null) {
                                            oldestPendingAge = Duration.between(minPendingTime, now);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.warn("Failed to query XPENDING stream={} group={}", streamKey, groupName, e);
                            }

                            groupMetrics.add(new ConsumerGroupMetrics(
                                    groupName, consumerCount, pCount, minPendingId, oldestPendingAge));
                        }
                    }
                }

                String healthStatus;
                if (isDlq) {
                    healthStatus = size > 0 ? "DLQ NONEMPTY" : "DLQ EMPTY";
                } else if (groupMetrics.isEmpty()) {
                    healthStatus = "REDIS_STREAM_NO_GROUP";
                } else {
                    boolean pendingTooOld = groupMetrics.stream().anyMatch(g ->
                            g.oldestPendingAge() != null && g.oldestPendingAge().compareTo(Duration.ofDays(1)) > 0);
                    if (pendingTooOld) {
                        healthStatus = "PENDING TOO OLD";
                    } else if (size > 1000) {
                        healthStatus = "BACKLOGGED";
                    } else {
                        healthStatus = "HEALTHY";
                    }
                }

                metricsList.add(new StreamHealthMetrics(
                        streamKey, size, firstId, oldestAge, lastId, newestAge, groupMetrics, healthStatus, true));

            } catch (Exception e) {
                LOGGER.error("Failed to inspect stream health for stream={}", streamKey, e);
                metricsList.add(new StreamHealthMetrics(
                        streamKey, -1L, "ERROR", null, "ERROR", null, List.of(), "REDIS ERROR", false));
            }
        }

        return metricsList;
    }

    public FeedCacheAuditResult auditFeedCacheTtlSample() {
        try {
            Set<String> sampleKeys = redis.execute((RedisCallback<Set<String>>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match("news:feed:*").count(20).build();
                Cursor<byte[]> cursor = connection.keyCommands().scan(options);
                List<String> keys = new ArrayList<>();
                while (cursor.hasNext() && keys.size() < 20) {
                    keys.add(new String(cursor.next()));
                }
                return Set.copyOf(keys);
            });

            if (sampleKeys == null || sampleKeys.isEmpty()) {
                return new FeedCacheAuditResult(0, null, null, "NO ACTIVE FEED CACHE SAMPLE AVAILABLE");
            }

            long minTtl = Long.MAX_VALUE;
            long maxTtl = Long.MIN_VALUE;
            int count = 0;

            for (String key : sampleKeys) {
                Long ttl = redis.getExpire(key);
                if (ttl != null && ttl >= 0) {
                    count++;
                    minTtl = Math.min(minTtl, ttl);
                    maxTtl = Math.max(maxTtl, ttl);
                }
            }

            if (count == 0) {
                return new FeedCacheAuditResult(0, null, null, "NO ACTIVE FEED CACHE SAMPLE AVAILABLE");
            }

            return new FeedCacheAuditResult(count, minTtl, maxTtl, "ACTIVE SAMPLE AUDITED");

        } catch (Exception e) {
            LOGGER.warn("Failed to audit feed cache TTL sample", e);
            return new FeedCacheAuditResult(0, null, null, "AUDIT ERROR: " + e.getMessage());
        }
    }

    public SchedulerHealthStatus getSchedulerHealthStatus() {
        boolean enabled = properties.enabled();
        boolean apply = properties.apply();
        boolean scheduleEnabled = properties.scheduleEnabled();
        String statusText = scheduleEnabled
                ? "ACTIVE (AUTONOMOUS 6-HOUR TRIMMING ENABLED)"
                : "IMPLEMENTED / MANUAL ACTIVATION REQUIRED";

        return new SchedulerHealthStatus(
                enabled,
                apply,
                scheduleEnabled,
                properties.scheduleCron(),
                statusText);
    }

    public List<RetentionWarning> detectTypedWarnings(
            RetentionMongoStorageService.MongoStorageOverview mongoOverview,
            List<RetentionMongoStorageService.TtlIndexHealthStatus> ttlHealthList,
            List<RetentionMongoStorageService.TtlDocumentLifecycleStatus> docLifecycleList,
            List<StreamHealthMetrics> streamMetricsList,
            RedisMemoryOverview redisMemory,
            SchedulerHealthStatus schedulerStatus) {

        List<RetentionWarning> warnings = new ArrayList<>();

        // 1. Mongo Warnings
        for (RetentionMongoStorageService.TtlIndexHealthStatus indexHealth : ttlHealthList) {
            if ("MISSING".equals(indexHealth.status())) {
                warnings.add(new RetentionWarning(
                        "MONGO_TTL_INDEX_MISSING",
                        "CRITICAL",
                        "MongoDB TTL index missing on collection: " + indexHealth.collectionName()));
            } else if ("CONFLICTING".equals(indexHealth.status())) {
                warnings.add(new RetentionWarning(
                        "MONGO_TTL_INDEX_CONFLICT",
                        "WARNING",
                        "MongoDB TTL index duration conflict on collection: " + indexHealth.collectionName()));
            }
        }

        for (RetentionMongoStorageService.TtlDocumentLifecycleStatus docLifecycle : docLifecycleList) {
            if (docLifecycle.expiredAwaitingCleanup() > 1000) {
                warnings.add(new RetentionWarning(
                        "MONGO_EXPIRED_BACKLOG",
                        "WARNING",
                        "High expired document backlog awaiting TTL cleanup in: " + docLifecycle.collectionName() + " (" + docLifecycle.expiredAwaitingCleanup() + " docs)"));
            }
            if ("notification_events".equals(docLifecycle.collectionName()) && docLifecycle.failedDeferred() > 0) {
                warnings.add(new RetentionWarning(
                        "FAILED_NOTIFICATION_EVENTS_UNBOUNDED",
                        "INFO",
                        "Failed NotificationEvent records without expiresAt exist (" + docLifecycle.failedDeferred() + " records). Awaiting canonical failure timestamp lifecycle design."));
            }
            if ("notification_events".equals(docLifecycle.collectionName()) && docLifecycle.unexpectedMissing() > 0) {
                warnings.add(new RetentionWarning(
                        "NOTIFICATION_EVENTS_UNEXPECTED_MISSING_EXPIRY",
                        "WARNING",
                        "NotificationEvent records unexpectedly missing expiresAt exist (" + docLifecycle.unexpectedMissing() + " records)."));
            }
        }

        // 2. Redis Warnings
        if (!redisMemory.available()) {
            warnings.add(new RetentionWarning(
                    "REDIS_UNAVAILABLE",
                    "CRITICAL",
                    "Redis / Valkey instance is unavailable or memory stats restricted. Core system operational but retention degraded."));
        }

        for (StreamHealthMetrics stream : streamMetricsList) {
            if (stream.streamName().contains("dlq") && stream.xlen() > 0) {
                warnings.add(new RetentionWarning(
                        "DLQ_NONEMPTY",
                        "WARNING",
                        "Dead-letter stream " + stream.streamName() + " contains " + stream.xlen() + " records. Manual review required (no auto-trim)."));
            }
            if (!stream.streamName().contains("dlq") && stream.consumerGroups().isEmpty() && stream.xlen() > 0) {
                warnings.add(new RetentionWarning(
                        "REDIS_STREAM_NO_GROUP",
                        "WARNING",
                        "Stream " + stream.streamName() + " has no registered consumer groups. Trimming is blocked to prevent data loss."));
            }
            for (ConsumerGroupMetrics group : stream.consumerGroups()) {
                if (group.oldestPendingAge() != null) {
                    if (group.oldestPendingAge().compareTo(Duration.ofDays(3)) > 0) {
                        warnings.add(new RetentionWarning(
                                "REDIS_PENDING_OLD",
                                "CRITICAL",
                                "Consumer group '" + group.groupName() + "' on stream '" + stream.streamName() + "' has pending message older than 3 days (" + group.oldestPendingAge().toDays() + " days). Retention protection boundary active."));
                    } else if (group.oldestPendingAge().compareTo(Duration.ofDays(1)) > 0) {
                        warnings.add(new RetentionWarning(
                                "REDIS_PENDING_OLD",
                                "WARNING",
                                "Consumer group '" + group.groupName() + "' on stream '" + stream.streamName() + "' has pending message older than 1 day."));
                    }
                }
            }
        }

        // 3. Scheduler Warnings
        if (!schedulerStatus.scheduleEnabled()) {
            warnings.add(new RetentionWarning(
                    "REDIS_SCHEDULER_DISABLED",
                    "INFO",
                    "Redis stream retention scheduler is disabled (schedule-enabled=false). Manual or external activation required for autonomous trimming."));
        }

        return warnings;
    }

    private Instant parseStreamTimestamp(String id) {
        RedisStreamID streamId = RedisStreamID.parse(id);
        if (streamId.isZero() || streamId.timestampMs() == 0L) {
            return null;
        }
        return Instant.ofEpochMilli(streamId.timestampMs());
    }

    private long parseLong(String val, long defaultVal) {
        if (val != null) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultVal;
    }

    public record RedisMemoryOverview(
            boolean available,
            long usedMemory,
            String usedMemoryHuman,
            long usedMemoryPeak,
            String usedMemoryPeakHuman,
            long maxmemory,
            String maxmemoryHuman,
            String maxmemoryPolicy) {
    }

    public record StreamHealthMetrics(
            String streamName,
            long xlen,
            String firstEntryId,
            Duration oldestEntryAge,
            String lastEntryId,
            Duration newestEntryAge,
            List<ConsumerGroupMetrics> consumerGroups,
            String status,
            boolean available) {
    }

    public record ConsumerGroupMetrics(
            String groupName,
            long consumerCount,
            long pendingCount,
            String minPendingId,
            Duration oldestPendingAge) {
    }

    public record FeedCacheAuditResult(
            int sampleCount,
            Long minTtlSeconds,
            Long maxTtlSeconds,
            String status) {
    }

    public record SchedulerHealthStatus(
            boolean retentionEnabled,
            boolean applyMode,
            boolean scheduleEnabled,
            String cronSchedule,
            String status) {
    }

    public record RetentionWarning(
            String warningCode,
            String severity,
            String message) {
    }
}
