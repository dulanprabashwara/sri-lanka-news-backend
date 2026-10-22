/*
 * File overview: Defines data returned by Bookmark Status API operations.
 * Area: reader preferences and personalization.
 */
package lk.srilankannews.user;

import java.time.Instant;

public record BookmarkStatusResponse(boolean bookmarked, Instant createdAt) {
}
