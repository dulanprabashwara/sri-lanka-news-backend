package lk.srilankannews.article;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class ArticleQueryRepositoryImpl implements ArticleQueryRepository {

    private final MongoTemplate mongoTemplate;

    public ArticleQueryRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Article> findAll(ArticleFilter filter, Pageable pageable) {
        long total = mongoTemplate.count(queryFor(filter), Article.class);
        List<Article> articles = mongoTemplate.find(queryFor(filter).with(pageable), Article.class);
        return new PageImpl<>(articles, pageable, total);
    }

    @Override
    public List<Article> findTrendingCandidates(
            Instant publishedSince, ArticleCategory category, int limit) {
        Criteria timeCriteria = new Criteria().orOperator(
                Criteria.where("publishedAt").gte(publishedSince),
                new Criteria().andOperator(
                        Criteria.where("publishedAt").is(null),
                        Criteria.where("discoveredAt").gte(publishedSince)
                )
        );
        Query query = new Query(timeCriteria);
        query.addCriteria(Criteria.where("title").ne(null).ne(""));
        if (category != null) {
            query.addCriteria(Criteria.where("category").is(category));
        }
        query.with(Sort.by(
                Sort.Order.desc("publishedAt"),
                Sort.Order.desc("id")));
        query.limit(limit);
        return mongoTemplate.find(query, Article.class);
    }

    private Query queryFor(ArticleFilter filter) {
        Query query = new Query();
        if (filter.sourceId() != null) {
            query.addCriteria(Criteria.where("sourceId").is(filter.sourceId()));
        }
        if (filter.category() != null) {
            query.addCriteria(Criteria.where("category").is(filter.category()));
        }
        if (filter.language() != null) {
            query.addCriteria(Criteria.where("originalLanguage").is(filter.language()));
        }
        return query;
    }

    @Override
    public List<Article> findRecentStoryArticles(java.util.Set<String> storyIds, Instant publishedSince) {
        if (storyIds == null || storyIds.isEmpty()) {
            return List.of();
        }
        Criteria timeCriteria = new Criteria().orOperator(
                Criteria.where("publishedAt").gte(publishedSince),
                new Criteria().andOperator(
                        Criteria.where("publishedAt").is(null),
                        Criteria.where("discoveredAt").gte(publishedSince)
                )
        );
        Query query = new Query(Criteria.where("storyId").in(storyIds));
        query.addCriteria(timeCriteria);
        query.addCriteria(Criteria.where("title").ne(null).ne(""));
        return mongoTemplate.find(query, Article.class);
    }

    @Override
    public java.util.Map<String, Long> countNewArticlesBySource(java.util.Map<String, Instant> sourceBaselines) {
        if (sourceBaselines == null || sourceBaselines.isEmpty()) {
            return java.util.Map.of();
        }

        Instant maxAllowedFuture = Instant.now().plus(java.time.Duration.ofMinutes(15));
        java.util.List<Criteria> sourceCriteriaList = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Instant> entry : sourceBaselines.entrySet()) {
            String sourceId = entry.getKey();
            Instant baseline = entry.getValue();
            if (sourceId == null || baseline == null) {
                continue;
            }

            Criteria validPublished = new Criteria().andOperator(
                    Criteria.where("publishedAt").gt(baseline),
                    Criteria.where("publishedAt").lte(maxAllowedFuture)
            );
            Criteria fallbackDiscovered = new Criteria().andOperator(
                    new Criteria().orOperator(
                            Criteria.where("publishedAt").is(null),
                            Criteria.where("publishedAt").gt(maxAllowedFuture)
                    ),
                    Criteria.where("discoveredAt").gt(baseline),
                    Criteria.where("discoveredAt").lte(maxAllowedFuture)
            );
            Criteria fallbackCreated = new Criteria().andOperator(
                    new Criteria().orOperator(
                            Criteria.where("publishedAt").is(null),
                            Criteria.where("publishedAt").gt(maxAllowedFuture)
                    ),
                    new Criteria().orOperator(
                            Criteria.where("discoveredAt").is(null),
                            Criteria.where("discoveredAt").gt(maxAllowedFuture)
                    ),
                    Criteria.where("createdAt").gt(baseline)
            );

            Criteria timeCriteria = new Criteria().orOperator(
                    validPublished,
                    fallbackDiscovered,
                    fallbackCreated
            );

            sourceCriteriaList.add(new Criteria().andOperator(
                    Criteria.where("sourceId").is(sourceId),
                    timeCriteria
            ));
        }

        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        sourceBaselines.keySet().forEach(s -> counts.put(s, 0L));

        if (sourceCriteriaList.isEmpty()) {
            return counts;
        }

        Criteria matchCriteria = new Criteria().andOperator(
                Criteria.where("title").ne(null).ne(""),
                new Criteria().orOperator(sourceCriteriaList.toArray(new Criteria[0]))
        );

        org.springframework.data.mongodb.core.aggregation.MatchOperation match =
                org.springframework.data.mongodb.core.aggregation.Aggregation.match(matchCriteria);
        org.springframework.data.mongodb.core.aggregation.GroupOperation group =
                org.springframework.data.mongodb.core.aggregation.Aggregation.group("sourceId").count().as("count");
        org.springframework.data.mongodb.core.aggregation.Aggregation aggregation =
                org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(match, group);

        org.springframework.data.mongodb.core.aggregation.AggregationResults<SourceArticleCountResult> results =
                mongoTemplate.aggregate(aggregation, Article.class, SourceArticleCountResult.class);

        for (SourceArticleCountResult result : results.getMappedResults()) {
            if (result.id() != null) {
                counts.put(result.id(), result.count());
            }
        }
        return counts;
    }

    private record SourceArticleCountResult(String id, long count) {}
}
