/*
 * File overview: Defines data returned by Recommendation Reason API operations.
 * Area: reader preferences and personalization.
 */
package lk.srilankannews.user;

public record RecommendationReasonResponse(RecommendationReasonType type, String label) {
}
