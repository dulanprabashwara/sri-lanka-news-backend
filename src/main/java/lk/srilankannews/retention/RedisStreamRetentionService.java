package lk.srilankannews.retention;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroup;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroups;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

@Service
public class RedisStreamRetentionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisStreamRetentionService.class);

    public static final String DEFAULT_ARTICLE_STREAM = "article-discovered";
    public static final String DEFAULT_NOTIFICATION_STREAM = "notification-events";
    public static final String DEFAULT_DLQ_STREAM = "article-discovered-dlq";

    private final StringRedisTemplate redis;
    private final RetentionRedisProperties properties;
    private final Clock clock;

    public RedisStreamRetentionService(
            StringRedisTemplate redis,
            RetentionRedisProperties properties,
            Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    public RedisStreamRetentionResult processStream(String streamKey, boolean overrideApply) {
        if (streamKey == null || streamKey.isBlank()) {
            return RedisStreamRetentionResult.skipped("UNKNOWN", "UNSAFE — STREAM KEY IS NULL OR BLANK");
        }

        if (streamKey.toLowerCase().contains("dlq")) {
            LOGGER.info("redis_retention_dlq_skipped stream={}", streamKey);
            return RedisStreamRetentionResult.skipped(
                    streamKey, "UNSAFE — DLQ STREAM CANNOT BE AUTOMATICALLY TRIMMED");
        }

        if (!properties.enabled()) {
            LOGGER.info("redis_retention_disabled stream={}", streamKey);
            return RedisStreamRetentionResult.skipped(streamKey, "RETENTION DISABLED IN CONFIGURATION");
        }

        try {
            Boolean exists = redis.hasKey(streamKey);
            if (Boolean.FALSE.equals(exists)) {
                return RedisStreamRetentionResult.skipped(streamKey, "STREAM DOES NOT EXIST IN REDIS");
            }

            Long initialXlen = redis.opsForStream().size(streamKey);
            if (initialXlen == null || initialXlen == 0) {
                return RedisStreamRetentionResult.skipped(streamKey, "STREAM IS EMPTY (XLEN = 0)");
            }

            String firstEntryId = getFirstStreamEntryId(streamKey);
            Duration historyDuration = getHistoryDuration(streamKey);
            Instant now = clock.instant();

            StreamAnalysis analysis = analyzeStream(streamKey);
            RedisStreamRetentionPolicy.PolicyResult policyResult = RedisStreamRetentionPolicy.calculateThreshold(
                    streamKey,
                    now,
                    historyDuration,
                    analysis.groupStates(),
                    analysis.pendingInconsistent(),
                    false);

            if (!policyResult.safeToTrim()) {
                LOGGER.warn("redis_retention_unsafe stream={} reason={}", streamKey, policyResult.reason());
                return new RedisStreamRetentionResult(
                        streamKey,
                        initialXlen,
                        initialXlen,
                        0L,
                        firstEntryId,
                        policyResult.ageCutoffId().rawId(),
                        policyResult.protectionBoundary().rawId(),
                        policyResult.finalThreshold().rawId(),
                        false,
                        policyResult.reason(),
                        false,
                        analysis.totalPending(),
                        analysis.totalPending(),
                        analysis.groupStates().size());
            }

            boolean shouldApply = properties.apply() || overrideApply;
            if (!shouldApply) {
                LOGGER.info("redis_retention_preview stream={} xlen={} firstId={} threshold={} safe=true",
                        streamKey, initialXlen, firstEntryId, policyResult.finalThreshold().rawId());
                return new RedisStreamRetentionResult(
                        streamKey,
                        initialXlen,
                        initialXlen,
                        0L,
                        firstEntryId,
                        policyResult.ageCutoffId().rawId(),
                        policyResult.protectionBoundary().rawId(),
                        policyResult.finalThreshold().rawId(),
                        true,
                        "PREVIEW MODE — OK",
                        false,
                        analysis.totalPending(),
                        analysis.totalPending(),
                        analysis.groupStates().size());
            }

            // Controlled Apply Mode: Immediately re-read state before XTRIM
            StreamAnalysis revalidatedAnalysis = analyzeStream(streamKey);
            RedisStreamRetentionPolicy.PolicyResult revalidatedPolicy = RedisStreamRetentionPolicy.calculateThreshold(
                    streamKey,
                    now,
                    historyDuration,
                    revalidatedAnalysis.groupStates(),
                    revalidatedAnalysis.pendingInconsistent(),
                    false);

            if (!revalidatedPolicy.safeToTrim()) {
                LOGGER.warn("redis_retention_apply_revalidation_failed stream={} reason={}",
                        streamKey, revalidatedPolicy.reason());
                return new RedisStreamRetentionResult(
                        streamKey,
                        initialXlen,
                        initialXlen,
                        0L,
                        firstEntryId,
                        revalidatedPolicy.ageCutoffId().rawId(),
                        revalidatedPolicy.protectionBoundary().rawId(),
                        revalidatedPolicy.finalThreshold().rawId(),
                        false,
                        "REVALIDATION FAILED: " + revalidatedPolicy.reason(),
                        false,
                        revalidatedAnalysis.totalPending(),
                        revalidatedAnalysis.totalPending(),
                        revalidatedAnalysis.groupStates().size());
            }

            String targetThreshold = revalidatedPolicy.finalThreshold().rawId();

            Long removed = executeExactXtrimMinId(streamKey, targetThreshold);
            Long finalXlen = redis.opsForStream().size(streamKey);
            String postFirstEntryId = getFirstStreamEntryId(streamKey);
            StreamAnalysis postAnalysis = analyzeStream(streamKey);

            LOGGER.info("redis_retention_trimmed stream={} preXlen={} postXlen={} removed={} preFirstId={} postFirstId={} threshold={}",
                    streamKey, initialXlen, finalXlen, removed, firstEntryId, postFirstEntryId, targetThreshold);

            return new RedisStreamRetentionResult(
                    streamKey,
                    initialXlen,
                    finalXlen != null ? finalXlen : 0L,
                    removed != null ? removed : 0L,
                    postFirstEntryId,
                    revalidatedPolicy.ageCutoffId().rawId(),
                    revalidatedPolicy.protectionBoundary().rawId(),
                    targetThreshold,
                    true,
                    "APPLIED SUCCESSFULLY",
                    true,
                    revalidatedAnalysis.totalPending(),
                    postAnalysis.totalPending(),
                    revalidatedAnalysis.groupStates().size());

        } catch (Exception e) {
            LOGGER.error("redis_retention_failed stream={} error={}", streamKey, e.getMessage(), e);
            return RedisStreamRetentionResult.skipped(streamKey, "REDIS ERROR: " + e.getMessage());
        }
    }

    public String getFirstStreamEntryId(String streamKey) {
        try {
            Range<String> unboundedRange = Range.unbounded();
            List<MapRecord<String, Object, Object>> firstList = redis.opsForStream().range(
                    streamKey, unboundedRange, Limit.limit().count(1));
            if (firstList != null && !firstList.isEmpty()) {
                return firstList.get(0).getId().getValue();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to retrieve first entry ID for stream={}", streamKey, e);
        }
        return "0-0";
    }

    public String getLastStreamEntryId(String streamKey) {
        try {
            Range<String> unboundedRange = Range.unbounded();
            List<MapRecord<String, Object, Object>> lastList = redis.opsForStream().reverseRange(
                    streamKey, unboundedRange, Limit.limit().count(1));
            if (lastList != null && !lastList.isEmpty()) {
                return lastList.get(0).getId().getValue();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to retrieve last entry ID for stream={}", streamKey, e);
        }
        return "0-0";
    }

    private Long executeExactXtrimMinId(String streamKey, String thresholdId) {
        byte[] keyBytes = StringRedisSerializer.UTF_8.serialize(streamKey);
        byte[] minIdOption = "MINID".getBytes(StandardCharsets.UTF_8);
        byte[] thresholdBytes = StringRedisSerializer.UTF_8.serialize(thresholdId);

        return redis.execute((RedisCallback<Long>) connection -> {
            Object result = connection.execute("XTRIM", keyBytes, minIdOption, thresholdBytes);
            if (result instanceof Number num) {
                return num.longValue();
            }
            return 0L;
        });
    }

    private StreamAnalysis analyzeStream(String streamKey) {
        XInfoGroups groups = redis.opsForStream().groups(streamKey);
        if (groups == null || groups.isEmpty()) {
            return new StreamAnalysis(Collections.emptyList(), 0L, false);
        }

        List<RedisStreamRetentionPolicy.GroupState> groupStates = new ArrayList<>();
        long totalPending = 0;
        boolean pendingInconsistent = false;

        for (XInfoGroup g : groups) {
            String groupName = g.groupName();
            String lastDelivered = g.lastDeliveredId() != null ? g.lastDeliveredId() : "0-0";

            long pCount = 0;
            String minPendingId = null;

            try {
                PendingMessagesSummary pendingSummary = redis.opsForStream().pending(streamKey, groupName);
                if (pendingSummary != null) {
                    pCount = pendingSummary.getTotalPendingMessages();
                    if (pCount > 0 && pendingSummary.minRecordId() != null) {
                        minPendingId = pendingSummary.minRecordId().getValue();
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to query XPENDING stream={} group={}", streamKey, groupName, e);
                pendingInconsistent = true;
            }

            totalPending += pCount;
            groupStates.add(new RedisStreamRetentionPolicy.GroupState(
                    groupName, lastDelivered, pCount, minPendingId));
        }

        return new StreamAnalysis(groupStates, totalPending, pendingInconsistent);
    }

    private Duration getHistoryDuration(String streamKey) {
        if (streamKey.equalsIgnoreCase(DEFAULT_NOTIFICATION_STREAM)) {
            return properties.notificationEvents().ackedHistoryDuration();
        }
        return properties.articleEvents().ackedHistoryDuration();
    }

    private record StreamAnalysis(
            List<RedisStreamRetentionPolicy.GroupState> groupStates,
            long totalPending,
            boolean pendingInconsistent) {
    }
}
