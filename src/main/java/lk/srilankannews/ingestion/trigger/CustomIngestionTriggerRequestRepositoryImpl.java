package lk.srilankannews.ingestion.trigger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class CustomIngestionTriggerRequestRepositoryImpl implements CustomIngestionTriggerRequestRepository {

    private final MongoTemplate mongoTemplate;

    public CustomIngestionTriggerRequestRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<IngestionTriggerRequest> claimNextPendingTrigger(String workerId, Instant now) {
        Query query = new Query(
                new Criteria().andOperator(
                        Criteria.where("status").is(IngestionTriggerRequest.STATUS_PENDING),
                        new Criteria().orOperator(
                                Criteria.where("nextAttemptAt").isNull(),
                                Criteria.where("nextAttemptAt").lte(now)
                        )
                )
        ).with(Sort.by(Sort.Direction.ASC, "requestedAt"));

        Update update = new Update()
                .set("status", IngestionTriggerRequest.STATUS_CLAIMED)
                .set("claimedAt", now)
                .set("workerId", workerId)
                .unset("nextAttemptAt")
                .inc("attemptCount", 1);

        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);

        IngestionTriggerRequest claimed = mongoTemplate.findAndModify(
                query, update, options, IngestionTriggerRequest.class
        );

        return Optional.ofNullable(claimed);
    }

    @Override
    public Optional<IngestionTriggerRequest> atomicEnqueueTrigger(String sourceId, String sourceSlug, String requestedBy, Instant now) {
        // Enforce at most ONE non-terminal manual trigger per source
        Query query = new Query(
                Criteria.where("sourceId").is(sourceId)
                        .and("status").in(List.of(IngestionTriggerRequest.STATUS_PENDING, IngestionTriggerRequest.STATUS_CLAIMED))
        );

        Update update = new Update()
                .setOnInsert("_id", new ObjectId().toHexString())
                .setOnInsert("sourceId", sourceId)
                .setOnInsert("sourceSlug", sourceSlug)
                .setOnInsert("requestedBy", requestedBy)
                .setOnInsert("requestedAt", now)
                .setOnInsert("status", IngestionTriggerRequest.STATUS_PENDING)
                .setOnInsert("attemptCount", 0)
                .setOnInsert("createdAt", now)
                .setOnInsert("_class", IngestionTriggerRequest.class.getName());

        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);

        IngestionTriggerRequest result = mongoTemplate.findAndModify(
                query, update, options, IngestionTriggerRequest.class
        );

        // If it was newly inserted, requestedAt will equal our `now` object exactly (or close enough that we know it's ours)
        // Better way: Check if the returned object has attemptCount == 0 and status PENDING
        if (result != null && result.requestedAt().equals(now)) {
            return Optional.of(result);
        }
        
        // If it was an existing one that got returned because upsert=true returns the existing one, we return empty to indicate it wasn't queued anew
        // Actually, if it existed, `setOnInsert` is ignored, and the existing document is returned.
        return Optional.empty(); // It existed already
    }
}
