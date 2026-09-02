package lk.srilankannews.story;

import java.util.List;
import java.time.Instant;
import lk.srilankannews.article.ArticleCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class StoryQueryRepositoryImpl implements StoryQueryRepository {

    private final MongoTemplate mongoTemplate;

    public StoryQueryRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Story> findAll(StoryFilter filter, Pageable pageable) {
        Query filterQuery = queryFor(filter);
        long total = mongoTemplate.count(filterQuery, Story.class);
        List<Story> stories = mongoTemplate.find(queryFor(filter).with(pageable), Story.class);
        return new PageImpl<>(stories, pageable, total);
    }

    @Override
    public List<Story> findTrendingCandidates(
            Instant publishedSince, ArticleCategory category, int limit) {
        Query query = Query.query(Criteria.where("articleCount").gt(0))
                .addCriteria(Criteria.where("lastPublishedAt").gte(publishedSince));
        if (category != null) {
            query.addCriteria(Criteria.where("category").is(category));
        }
        query.with(Sort.by(
                Sort.Order.desc("lastPublishedAt"),
                Sort.Order.desc("id")));
        query.limit(limit);
        return mongoTemplate.find(query, Story.class);
    }

    private Query queryFor(StoryFilter filter) {
        Query query = Query.query(Criteria.where("articleCount").gt(0));
        if (filter.category() != null) {
            query.addCriteria(Criteria.where("category").is(filter.category()));
        }
        if (filter.publishedFrom() != null || filter.publishedTo() != null) {
            Criteria published = Criteria.where("lastPublishedAt");
            if (filter.publishedFrom() != null) {
                published.gte(filter.publishedFrom());
            }
            if (filter.publishedTo() != null) {
                published.lte(filter.publishedTo());
            }
            query.addCriteria(published);
        }
        return query;
    }
}
