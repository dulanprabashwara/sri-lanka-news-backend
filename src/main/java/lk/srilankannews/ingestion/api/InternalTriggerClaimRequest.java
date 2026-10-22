/*
 * File overview: Defines and validates input accepted by Internal Trigger Claim operations.
 * Area: ingestion coordination and run tracking.
 */
package lk.srilankannews.ingestion.api;

public record InternalTriggerClaimRequest(
        String workerId
) {}
