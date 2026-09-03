package lk.srilankannews.ingestion.api;

public record InternalSourceConfigResponse(
        String sourceSlug,
        boolean enabled,
        int intervalMinutes,
        int jitterSeconds
) {}
