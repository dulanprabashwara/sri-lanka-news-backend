package lk.srilankannews.admin;

import java.time.Instant;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;

public record AdminSourceResponse(
        String id,
        String name,
        String slug,
        String baseUrl,
        Language defaultLanguage,
        IngestionType ingestionType,
        boolean enabled,
        long articleCount,
        Instant createdAt,
        Instant updatedAt) {
}
