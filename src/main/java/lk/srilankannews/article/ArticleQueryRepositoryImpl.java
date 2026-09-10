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
}
