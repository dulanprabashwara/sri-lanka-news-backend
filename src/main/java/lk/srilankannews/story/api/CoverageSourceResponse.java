/*
 * File overview: Defines data returned by Coverage Source API operations.
 * Area: story clustering, timelines, comparison, and Q&A.
 */
package lk.srilankannews.story.api;

public record CoverageSourceResponse(
        String name,
        String slug
) {
}
