/*
 * File overview: Defines data returned by Source Summary API operations.
 * Area: publisher source registration and APIs.
 */
package lk.srilankannews.source.api;

public record SourceSummaryResponse(
        String name,
        String slug,
        String baseUrl
) {
}
