/*
 * File overview: Defines MongoDB persistence and query operations for Ingestion Trigger Request.
 * Area: ingestion coordination and run tracking.
 */
package lk.srilankannews.ingestion.trigger;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IngestionTriggerRequestRepository extends MongoRepository<IngestionTriggerRequest, String>, CustomIngestionTriggerRequestRepository {

    // For checking bounds/duplicates safely
    boolean existsBySourceSlugAndStatusIn(String sourceSlug, java.util.Collection<String> statuses);

    Optional<IngestionTriggerRequest> findFirstBySourceSlugAndStatusInOrderByRequestedAtDesc(String sourceSlug, java.util.Collection<String> statuses);
}
