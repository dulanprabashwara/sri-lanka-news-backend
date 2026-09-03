package lk.srilankannews.ingestion.run.api;

import java.time.Instant;

public record ClaimResponse(
        boolean claimed,
        String runId,
        Instant leaseExpiresAt,
        String reason
) {
    public static ClaimResponse success(String runId, Instant leaseExpiresAt) {
        return new ClaimResponse(true, runId, leaseExpiresAt, null);
    }

    public static ClaimResponse activeRun() {
        return new ClaimResponse(false, null, null, "ACTIVE_RUN");
    }
}
