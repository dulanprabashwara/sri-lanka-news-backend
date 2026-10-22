/*
 * File overview: Defines data returned by Internal Trigger Claim API operations.
 * Area: ingestion coordination and run tracking.
 */
package lk.srilankannews.ingestion.api;

public record InternalTriggerClaimResponse(
        boolean claimed,
        String triggerId,
        String sourceSlug
) {}
