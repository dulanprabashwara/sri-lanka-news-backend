/*
 * File overview: Defines data returned by Personalization API operations.
 * Area: reader preferences and personalization.
 */
package lk.srilankannews.user;

public record PersonalizationResponse(boolean personalized, int signalCount) {
}
