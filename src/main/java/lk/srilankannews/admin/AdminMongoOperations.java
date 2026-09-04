package lk.srilankannews.admin;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.source.Source;
import lk.srilankannews.story.Story;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class AdminMongoOperations {

    private final MongoOperations mongo;

    public AdminMongoOperations(MongoOperations mongo) {
        this.mongo = mongo;
    }

    public long sourceCount() {
        return mongo.count(new Query(), Source.class);
    }

    public long storyCount() {
        return mongo.count(Query.query(Criteria.where("articleCount").gt(0)), Story.class);
    }

    public long storyCountSince(Instant since) {
        return mongo.count(Query.query(Criteria.where("createdAt").gte(since)), Story.class);
    }

    public long storyActiveSince(Instant since) {
        return mongo.count(Query.query(Criteria.where("lastPublishedAt").gte(since)), Story.class);
    }

    public long articleCount() {
        return mongo.count(new Query(), Article.class);
    }

    public long articleCount(ProcessingStatus status) {
        return mongo.count(Query.query(Criteria.where("processingStatus").is(status)), Article.class);
    }

    public List<Article> findArticles(
            ProcessingStatus status, String sourceId, int limit) {
        Query query = new Query();
        if (status != null) query.addCriteria(Criteria.where("processingStatus").is(status));
        if (sourceId != null) query.addCriteria(Criteria.where("sourceId").is(sourceId));
        query.with(Sort.by(Sort.Order.desc("discoveredAt"), Sort.Order.desc("id")));
        query.limit(limit);
        return mongo.find(query, Article.class);
    }

    public Map<String, Long> articleCountsBySource() {
        List<Document> results = mongo.aggregate(
                newAggregation(group("sourceId").count().as("count")),
                Article.class, Document.class).getMappedResults();
        Map<String, Long> counts = new LinkedHashMap<>();
        results.forEach(result -> counts.put(
                result.getString("_id"), ((Number) result.get("count")).longValue()));
        return Map.copyOf(counts);
    }

    public Article claimFailedForRetry(String articleId, Instant now) {
        Query query = Query.query(Criteria.where("_id").is(articleId)
                .and("processingStatus").is(ProcessingStatus.FAILED));
        Update update = new Update()
                .set("processingStatus", ProcessingStatus.RETRYING)
                .set("updatedAt", now);
        return mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Article.class);
    }

    public org.springframework.data.domain.Page<Article> findArticlesPage(
            ProcessingStatus status, String sourceId, org.springframework.data.domain.Pageable pageable) {
        Query query = new Query();
        if (status != null) query.addCriteria(Criteria.where("processingStatus").is(status));
        if (sourceId != null) query.addCriteria(Criteria.where("sourceId").is(sourceId));
        
        long total = mongo.count(query, Article.class);
        query.with(pageable);
        List<Article> content = mongo.find(query, Article.class);
        return org.springframework.data.support.PageableExecutionUtils.getPage(
                content, pageable, () -> total);
    }

    public org.springframework.data.domain.Page<Story> findStoriesPage(org.springframework.data.domain.Pageable pageable) {
        Query query = new Query();
        long total = mongo.count(query, Story.class);
        query.with(pageable);
        List<Story> content = mongo.find(query, Story.class);
        return org.springframework.data.support.PageableExecutionUtils.getPage(
                content, pageable, () -> total);
    }

    public long userPreferencesCount() {
        return mongo.count(new Query(), "user_preferences");
    }

    public long uniqueUsersWithBookmarks() {
        return mongo.query(lk.srilankannews.user.UserBookmark.class).distinct("userId").as(String.class).all().size();
    }

    public long totalBookmarks() {
        return mongo.count(new Query(), lk.srilankannews.user.UserBookmark.class);
    }

    public long totalFollows() {
        return mongo.count(new Query(), lk.srilankannews.user.UserFollow.class);
    }
}
