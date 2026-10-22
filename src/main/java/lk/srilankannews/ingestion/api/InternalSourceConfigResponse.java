/*
 * File overview: Defines data returned by Internal Source Config API operations.
 * Area: ingestion coordination and run tracking.
 */
package lk.srilankannews.ingestion.api;

public record InternalSourceConfigResponse(
        String sourceSlug,
        boolean enabled,
        int intervalMinutes,
        int jitterSeconds
) {}
