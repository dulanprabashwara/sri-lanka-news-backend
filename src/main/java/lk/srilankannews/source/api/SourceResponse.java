package lk.srilankannews.source.api;

import lk.srilankannews.common.domain.Language;

public record SourceResponse(
        String name,
        String slug,
        String baseUrl,
        Language defaultLanguage
) {
}
