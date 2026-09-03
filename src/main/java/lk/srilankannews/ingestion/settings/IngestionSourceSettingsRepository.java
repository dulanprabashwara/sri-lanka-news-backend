package lk.srilankannews.ingestion.settings;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface IngestionSourceSettingsRepository extends MongoRepository<IngestionSourceSettings, String> {
    Optional<IngestionSourceSettings> findBySourceId(String sourceId);
    Optional<IngestionSourceSettings> findBySourceSlug(String sourceSlug);
}
