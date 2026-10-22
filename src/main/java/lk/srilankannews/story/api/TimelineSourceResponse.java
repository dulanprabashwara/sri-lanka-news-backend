/*
 * File overview: Defines data returned by Timeline Source API operations.
 * Area: story clustering, timelines, comparison, and Q&A.
 */
package lk.srilankannews.story.api;

public record TimelineSourceResponse(
        String name,
        String slug
) {
}
