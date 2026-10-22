/*
 * File overview: Defines data returned by Coverage Entity API operations.
 * Area: story clustering, timelines, comparison, and Q&A.
 */
package lk.srilankannews.story.api;

public record CoverageEntityResponse(
        String name,
        String type
) {
}
