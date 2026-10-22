/*
 * File overview: Defines data returned by Topic Follow Status API operations.
 * Area: reader preferences and personalization.
 */
package lk.srilankannews.user;

import java.time.Instant;

public record TopicFollowStatusResponse(String topic, boolean followed, Instant followedAt) {
}
