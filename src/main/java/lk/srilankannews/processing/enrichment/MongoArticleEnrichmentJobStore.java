package lk.srilankannews.processing.enrichment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoArticleEnrichmentJobStore implements ArticleEnrichmentJobStore {
    static final String COLLECTION_NAME = "article_enrichment_jobs";
    private final MongoOperations mongo;

    public MongoArticleEnrichmentJobStore(MongoOperations mongo) {
        this.mongo = mongo;
    }

    @Override
    public void ensurePending(String articleId, Instant now) {
        try {
            mongo.insert(new ArticleEnrichmentJob(
                    articleId, EnrichmentStatus.PENDING, 0, now,
                    null, null, null, now));
        } catch (DuplicateKeyException ignored) {
            // The article already has durable enrichment state.
        }
    }

    @Override
    public Optional<ArticleEnrichmentJob> claim(
            String articleId, Instant now, Duration leaseDuration, int maxAttempts) {
        Criteria available = new Criteria().orOperator(
                Criteria.where("status").is(EnrichmentStatus.PENDING),
                new Criteria().andOperator(
                        Criteria.where("status").is(EnrichmentStatus.DEFERRED),
                        Criteria.where("nextRetryAt").lte(now)),
                new Criteria().andOperator(
                        Criteria.where("status").is(EnrichmentStatus.PROCESSING),
                        Criteria.where("leaseUntil").lte(now)));
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(articleId),
                Criteria.where("attempts").lt(maxAttempts),
                available));
        String claimToken = UUID.randomUUID().toString();
        Update update = new Update()
                .set("status", EnrichmentStatus.PROCESSING)
                .set("claimToken", claimToken)
                .set("leaseUntil", now.plus(leaseDuration))
                .set("updatedAt", now)
                .unset("nextRetryAt")
                .inc("attempts", 1);
        return Optional.ofNullable(mongo.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                ArticleEnrichmentJob.class));
    }

    @Override
    public List<String> findDueArticleIds(Instant now, int maxAttempts, int limit) {
        Criteria due = new Criteria().orOperator(
                new Criteria().andOperator(
                        Criteria.where("status").is(EnrichmentStatus.DEFERRED),
                        Criteria.where("nextRetryAt").lte(now)),
                new Criteria().andOperator(
                        Criteria.where("status").is(EnrichmentStatus.PROCESSING),
                        Criteria.where("leaseUntil").lte(now)));
        Query query = Query.query(new Criteria().andOperator(
                        Criteria.where("attempts").lt(maxAttempts), due))
                .with(Sort.by(Sort.Order.asc("nextRetryAt"), Sort.Order.asc("updatedAt")))
                .limit(limit);
        query.fields().include("_id");
        return mongo.find(query, Document.class, COLLECTION_NAME).stream()
                .map(doc -> doc.get("_id"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
    }

    @Override
    public void markSucceeded(String articleId, String claimToken, Instant now) {
        updateClaim(articleId, claimToken, new Update()
                .set("status", EnrichmentStatus.SUCCEEDED)
                .set("updatedAt", now)
                .unset("claimToken")
                .unset("leaseUntil")
                .unset("nextRetryAt")
                .unset("lastFailureKind"));
    }

    @Override
    public void markAlreadySucceeded(String articleId, Instant now) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(articleId)), new Update()
                .set("status", EnrichmentStatus.SUCCEEDED)
                .set("updatedAt", now)
                .unset("claimToken")
                .unset("leaseUntil")
                .unset("nextRetryAt")
                .unset("lastFailureKind"), ArticleEnrichmentJob.class);
    }

    @Override
    public void markDeferred(
            String articleId, String claimToken, String failureKind,
            Instant nextRetryAt, Instant now) {
        updateClaim(articleId, claimToken, new Update()
                .set("status", EnrichmentStatus.DEFERRED)
                .set("lastFailureKind", failureKind)
                .set("nextRetryAt", nextRetryAt)
                .set("updatedAt", now)
                .unset("claimToken")
                .unset("leaseUntil"));
    }

    @Override
    public void markFailed(String articleId, String claimToken, String failureKind, Instant now) {
        updateClaim(articleId, claimToken, new Update()
                .set("status", EnrichmentStatus.FAILED)
                .set("lastFailureKind", failureKind)
                .set("updatedAt", now)
                .unset("claimToken")
                .unset("leaseUntil")
                .unset("nextRetryAt"));
    }

    private void updateClaim(String articleId, String claimToken, Update update) {
        mongo.updateFirst(Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(articleId),
                        Criteria.where("status").is(EnrichmentStatus.PROCESSING),
                        Criteria.where("claimToken").is(claimToken))),
                update, ArticleEnrichmentJob.class);
    }
}
