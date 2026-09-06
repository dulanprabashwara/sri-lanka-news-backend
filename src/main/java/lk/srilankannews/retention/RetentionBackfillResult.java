package lk.srilankannews.retention;

public record RetentionBackfillResult(
        String collectionName,
        long examinedCount,
        long eligibleCount,
        long updatedCount,
        long skippedCount,
        long deferredCount,
        long errorCount,
        long immediateExpiryCandidateCount
) {
    public static RetentionBackfillResult empty(String collectionName) {
        return new RetentionBackfillResult(collectionName, 0, 0, 0, 0, 0, 0, 0);
    }
}
