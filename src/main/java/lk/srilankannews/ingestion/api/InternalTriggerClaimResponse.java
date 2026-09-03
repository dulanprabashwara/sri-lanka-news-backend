package lk.srilankannews.ingestion.api;

public record InternalTriggerClaimResponse(
        boolean claimed,
        String triggerId,
        String sourceSlug
) {}
