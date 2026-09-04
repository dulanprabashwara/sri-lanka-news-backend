package lk.srilankannews.ingestion.run;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionRunRepository extends MongoRepository<IngestionRun, String> {

    List<IngestionRun> findBySourceIdAndStatusAndLeaseExpiresAtBefore(
            String sourceId, IngestionRunStatus status, Instant before);

    org.springframework.data.domain.Page<IngestionRun> findBySourceSlug(
            String sourceSlug, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<IngestionRun> findBySourceSlugAndStatus(
            String sourceSlug, IngestionRunStatus status, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<IngestionRun> findBySourceSlugAndTriggerType(
            String sourceSlug, IngestionTriggerType triggerType, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<IngestionRun> findBySourceSlugAndStatusAndTriggerType(
            String sourceSlug, IngestionRunStatus status, IngestionTriggerType triggerType, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<IngestionRun> findByStatus(
            IngestionRunStatus status, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<IngestionRun> findByTriggerType(
            IngestionTriggerType triggerType, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<IngestionRun> findByStatusAndTriggerType(
            IngestionRunStatus status, IngestionTriggerType triggerType, org.springframework.data.domain.Pageable pageable);

    // For health calculations:
    List<IngestionRun> findTop5BySourceSlugOrderByStartedAtDesc(String sourceSlug);

    long countByStatus(IngestionRunStatus status);
}
