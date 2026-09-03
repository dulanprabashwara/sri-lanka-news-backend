package lk.srilankannews.ingestion.run;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class IngestionSourceLeaseRepository {

    private final MongoTemplate mongoTemplate;

    public IngestionSourceLeaseRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Atomically acquires or renews a lease for a source.
     * Succeeds if:
     * 1. No lease exists for the source.
     * 2. The existing lease has expired (expiresAt < now).
     * 
     * Uses UPSERT.
     */
    public Optional<IngestionSourceLease> acquireLease(
            String sourceId, String runId, String workerId, Instant expiresAt, Instant now) {
        Query query = new Query(Criteria.where("_id").is(sourceId)
                .orOperator(
                        Criteria.where("expiresAt").exists(false),
                        Criteria.where("expiresAt").lt(now)
                ));

        Update update = new Update()
                .setOnInsert("_id", sourceId)
                .set("runId", runId)
                .set("workerId", workerId)
                .set("expiresAt", expiresAt);

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);

        IngestionSourceLease result = mongoTemplate.findAndModify(query, update, options, IngestionSourceLease.class);
        return Optional.ofNullable(result);
    }

    /**
     * Atomically extends a lease ONLY if it belongs to the specified runId.
     */
    public Optional<IngestionSourceLease> extendLease(String sourceId, String runId, Instant newExpiresAt) {
        Query query = new Query(Criteria.where("_id").is(sourceId).and("runId").is(runId));
        Update update = new Update().set("expiresAt", newExpiresAt);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        
        IngestionSourceLease result = mongoTemplate.findAndModify(query, update, options, IngestionSourceLease.class);
        return Optional.ofNullable(result);
    }

    /**
     * Atomically releases a lease ONLY if it belongs to the specified runId.
     */
    public void releaseLease(String sourceId, String runId) {
        Query query = new Query(Criteria.where("_id").is(sourceId).and("runId").is(runId));
        mongoTemplate.remove(query, IngestionSourceLease.class);
    }

    public Optional<IngestionSourceLease> findById(String sourceId) {
        return Optional.ofNullable(mongoTemplate.findById(sourceId, IngestionSourceLease.class));
    }
}
