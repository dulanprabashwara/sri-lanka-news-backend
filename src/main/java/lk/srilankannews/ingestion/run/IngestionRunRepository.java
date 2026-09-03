package lk.srilankannews.ingestion.run;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionRunRepository extends MongoRepository<IngestionRun, String> {

    List<IngestionRun> findBySourceIdAndStatusAndLeaseExpiresAtBefore(
            String sourceId, IngestionRunStatus status, Instant before);
}
