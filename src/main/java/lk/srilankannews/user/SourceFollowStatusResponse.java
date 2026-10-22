/*
 * File overview: Defines data returned by Source Follow Status API operations.
 * Area: reader preferences and personalization.
 */
package lk.srilankannews.user;

import java.time.Instant;

public record SourceFollowStatusResponse(String slug, boolean followed, Instant followedAt) {
}
