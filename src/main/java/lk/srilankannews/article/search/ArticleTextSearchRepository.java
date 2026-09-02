package lk.srilankannews.article.search;

import java.util.List;
import lk.srilankannews.article.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Repository;

@Repository
public class ArticleTextSearchRepository {
    static final String SCORE_FIELD = "_textScore";

    private final MongoOperations mongoOperations;

    public ArticleTextSearchRepository(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    public Page<Article> search(
            String normalizedQuery, ArticleSearchFilter filter, Pageable pageable) {
        TextCriteria text = TextCriteria.forDefaultLanguage().matching(normalizedQuery);
        Query countQuery = Query.query(text);
        applyFilters(countQuery, filter);
        long total = mongoOperations.count(countQuery, Article.class);

        TextQuery searchQuery = TextQuery.queryText(text).includeScore(SCORE_FIELD).sortByScore();
        applyFilters(searchQuery, filter);
        searchQuery.with(Sort.by(
                Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
        searchQuery.with(pageable);
        List<Article> articles = mongoOperations.find(searchQuery, Article.class);
        return new PageImpl<>(articles, pageable, total);
    }

    private void applyFilters(Query query, ArticleSearchFilter filter) {
        if (filter.sourceId() != null) {
            query.addCriteria(Criteria.where("sourceId").is(filter.sourceId()));
        }
        if (filter.category() != null) {
            query.addCriteria(Criteria.where("category").is(filter.category()));
        }
        if (filter.language() != null) {
            query.addCriteria(Criteria.where("originalLanguage").is(filter.language()));
        }
    }
}
