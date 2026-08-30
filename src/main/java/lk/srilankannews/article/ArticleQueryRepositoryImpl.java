package lk.srilankannews.article;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
}
