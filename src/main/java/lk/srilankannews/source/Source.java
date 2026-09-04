package lk.srilankannews.source;

import java.time.Instant;
import lk.srilankannews.common.domain.Language;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "sources")
public record Source(
        @Id String id,
        String name,
        @Indexed(name = "uk_sources_slug", unique = true) String slug,
        String baseUrl,
        Language defaultLanguage,
        IngestionType ingestionType,
        boolean enabled,
        SourceImagePolicy imagePolicy,
        Instant createdAt,
        Instant updatedAt
) {
    public Source(
            String id, String name, String slug, String baseUrl, Language defaultLanguage,
            IngestionType ingestionType, boolean enabled, Instant createdAt, Instant updatedAt) {
        this(id, name, slug, baseUrl, defaultLanguage, ingestionType, enabled,
                new SourceImagePolicy(false, java.util.Set.of()), createdAt, updatedAt);
    }
    static Source create(CreateSourceCommand command, Instant now) {
        return new Source(
                null,
                command.name(),
                command.slug(),
                command.baseUrl(),
                command.defaultLanguage(),
                command.ingestionType(),
                command.enabled(),
                command.imagePolicy(),
                now,
                now);
    }

    Source withEnabled(boolean newEnabled, Instant now) {
        return new Source(
                id,
                name,
                slug,
                baseUrl,
                defaultLanguage,
                ingestionType,
                newEnabled,
                imagePolicy,
                createdAt,
                now);
    }
}
