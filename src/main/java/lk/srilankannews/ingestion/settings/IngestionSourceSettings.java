package lk.srilankannews.ingestion.settings;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ingestion_source_settings")
public record IngestionSourceSettings(
        @Id String id,
        @Indexed(name = "uk_settings_sourceId", unique = true) String sourceId,
        @Indexed(name = "uk_settings_sourceSlug", unique = true) String sourceSlug,
        boolean enabled,
        int intervalMinutes,
        int jitterSeconds,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy
) {
    public IngestionSourceSettings withUpdates(boolean enabled, int intervalMinutes, int jitterSeconds, String updatedBy, Instant now) {
        return new IngestionSourceSettings(
                id, sourceId, sourceSlug, enabled, intervalMinutes, jitterSeconds, createdAt, now, updatedBy
        );
    }
}
