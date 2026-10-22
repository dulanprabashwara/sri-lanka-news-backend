/*
 * File overview: Defines immutable Retention Backfill Result data passed between backend components.
 * Area: MongoDB and Redis data retention.
 */
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
