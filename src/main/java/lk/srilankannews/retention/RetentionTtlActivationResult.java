package lk.srilankannews.retention;

public record RetentionTtlActivationResult(
        String collectionName,
        String indexName,
        String targetField,
        long expireAfterSeconds,
        Status status,
        long preTotalCount,
        long preWithExpiresCount,
        long preImmediateExpiredCount,
        long postTotalCount,
        String error
) {
    public enum Status {
        CREATED,
        EXISTING_MATCH,
        SKIPPED,
        FAILED
    }

    public static RetentionTtlActivationResult created(
            String collectionName, String indexName, String targetField, long expireAfterSeconds,
            long preTotalCount, long preWithExpiresCount, long preImmediateExpiredCount, long postTotalCount) {
        return new RetentionTtlActivationResult(
                collectionName, indexName, targetField, expireAfterSeconds,
                Status.CREATED, preTotalCount, preWithExpiresCount, preImmediateExpiredCount, postTotalCount, null);
    }

    public static RetentionTtlActivationResult existingMatch(
            String collectionName, String indexName, String targetField, long expireAfterSeconds,
            long preTotalCount, long preWithExpiresCount, long preImmediateExpiredCount, long postTotalCount) {
        return new RetentionTtlActivationResult(
                collectionName, indexName, targetField, expireAfterSeconds,
                Status.EXISTING_MATCH, preTotalCount, preWithExpiresCount, preImmediateExpiredCount, postTotalCount, null);
    }

    public static RetentionTtlActivationResult skipped(
            String collectionName, String indexName, String targetField, long expireAfterSeconds,
            long preTotalCount, long preWithExpiresCount, long preImmediateExpiredCount) {
        return new RetentionTtlActivationResult(
                collectionName, indexName, targetField, expireAfterSeconds,
                Status.SKIPPED, preTotalCount, preWithExpiresCount, preImmediateExpiredCount, preTotalCount, null);
    }

    public static RetentionTtlActivationResult failed(
            String collectionName, String indexName, String targetField, long expireAfterSeconds,
            long preTotalCount, long preWithExpiresCount, long preImmediateExpiredCount, String error) {
        return new RetentionTtlActivationResult(
                collectionName, indexName, targetField, expireAfterSeconds,
                Status.FAILED, preTotalCount, preWithExpiresCount, preImmediateExpiredCount, preTotalCount, error);
    }
}
