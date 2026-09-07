package lk.srilankannews.retention;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class RedisStreamRetentionPolicy {

    private RedisStreamRetentionPolicy() {
    }

    public record GroupState(
            String groupName,
            String lastDeliveredId,
            long pendingCount,
            String oldestPendingId) {
    }

    public record PolicyResult(
            boolean safeToTrim,
            String reason,
            RedisStreamID ageCutoffId,
            RedisStreamID protectionBoundary,
            RedisStreamID finalThreshold) {
    }

    public static PolicyResult calculateThreshold(
            String streamKey,
            Instant now,
            Duration ackedHistoryDuration,
            List<GroupState> groupStates,
            boolean pendingBodyInconsistent,
            boolean isDlqStream) {

        if (isDlqStream) {
            return new PolicyResult(
                    false,
                    "UNSAFE — DLQ STREAM CANNOT BE AUTOMATICALLY TRIMMED",
                    RedisStreamID.ZERO,
                    RedisStreamID.ZERO,
                    RedisStreamID.ZERO);
        }

        if (groupStates == null || groupStates.isEmpty()) {
            return new PolicyResult(
                    false,
                    "UNSAFE — NO CONSUMER GROUP",
                    RedisStreamID.ZERO,
                    RedisStreamID.ZERO,
                    RedisStreamID.ZERO);
        }

        if (pendingBodyInconsistent) {
            return new PolicyResult(
                    false,
                    "UNSAFE — PENDING ENTRY BODY MISSING / STREAM STATE INCONSISTENT",
                    RedisStreamID.ZERO,
                    RedisStreamID.ZERO,
                    RedisStreamID.ZERO);
        }

        long ageCutoffMs = Math.max(0L, now.toEpochMilli() - ackedHistoryDuration.toMillis());
        RedisStreamID ageCutoffId = RedisStreamID.fromEpochMilli(ageCutoffMs);

        RedisStreamID minGroupBoundary = null;

        for (GroupState group : groupStates) {
            RedisStreamID groupBoundary;
            if (group.pendingCount() > 0) {
                if (group.oldestPendingId() == null || group.oldestPendingId().isBlank()) {
                    groupBoundary = RedisStreamID.ZERO;
                } else {
                    groupBoundary = RedisStreamID.parse(group.oldestPendingId());
                }
            } else {
                if (group.lastDeliveredId() == null || group.lastDeliveredId().isBlank()) {
                    groupBoundary = RedisStreamID.ZERO;
                } else {
                    groupBoundary = RedisStreamID.parse(group.lastDeliveredId());
                }
            }

            if (minGroupBoundary == null || groupBoundary.compareTo(minGroupBoundary) < 0) {
                minGroupBoundary = groupBoundary;
            }
        }

        if (minGroupBoundary == null || minGroupBoundary.isZero()) {
            return new PolicyResult(
                    false,
                    "UNSAFE — GROUP BOUNDARY UNKNOWN OR AT 0-0",
                    ageCutoffId,
                    minGroupBoundary != null ? minGroupBoundary : RedisStreamID.ZERO,
                    RedisStreamID.ZERO);
        }

        RedisStreamID finalThreshold = (ageCutoffId.compareTo(minGroupBoundary) < 0)
                ? ageCutoffId
                : minGroupBoundary;

        if (finalThreshold.isZero()) {
            return new PolicyResult(
                    false,
                    "UNSAFE — CALCULATED THRESHOLD IS 0-0",
                    ageCutoffId,
                    minGroupBoundary,
                    RedisStreamID.ZERO);
        }

        return new PolicyResult(
                true,
                "OK",
                ageCutoffId,
                minGroupBoundary,
                finalThreshold);
    }
}
