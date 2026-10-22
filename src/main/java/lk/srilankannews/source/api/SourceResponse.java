/*
 * File overview: Defines data returned by Source API operations.
 * Area: publisher source registration and APIs.
 */
package lk.srilankannews.source.api;

import lk.srilankannews.common.domain.Language;

public record SourceResponse(
        String name,
        String slug,
        String baseUrl,
        Language defaultLanguage
) {
}
