package lk.srilankannews.retention;

public record RedisStreamRetentionResult(
        String streamKey,
        long initialXlen,
        long finalXlen,
        long removedCount,
        String firstEntryId,
        String ageCutoffId,
        String protectionBoundary,
        String finalThreshold,
        boolean safeToTrim,
        String reason,
        boolean applied,
        long pendingCountBefore,
        long pendingCountAfter,
        int consumerGroupsCount) {

    public static RedisStreamRetentionResult skipped(String streamKey, String reason) {
        return new RedisStreamRetentionResult(
                streamKey,
                0L,
                0L,
                0L,
                "0-0",
                "0-0",
                "0-0",
                "0-0",
                false,
                reason,
                false,
                0L,
                0L,
                0);
    }
}
