package lk.srilankannews.article;

import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(MongoOperations.class)
public class ArticleContentHashBackfill implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleContentHashBackfill.class);
    private static final String COLLECTION = "articles";
    private final MongoOperations mongo;
    private final ArticleContentHasher hasher;

    public ArticleContentHashBackfill(MongoOperations mongo, ArticleContentHasher hasher) {
        this.mongo = mongo;
        this.hasher = hasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        Query missingHash = Query.query(Criteria.where("contentHash").exists(false)
                .and("extractedContent").ne(null));
        List<Document> articles = mongo.find(missingHash, Document.class, COLLECTION);
        int updated = 0;
        int skipped = 0;
        for (Document article : articles) {
            String content = article.getString("extractedContent");
            if (content == null || content.isBlank()) {
                continue;
            }
            String hash = hasher.hash(content);
            if (mongo.exists(Query.query(Criteria.where("contentHash").is(hash)), COLLECTION)) {
                skipped++;
                continue;
            }
            Query byIdWithoutHash = Query.query(Criteria.where("_id").is(article.get("_id"))
                    .and("contentHash").exists(false));
            try {
                updated += mongo.updateFirst(byIdWithoutHash, Update.update("contentHash", hash), COLLECTION)
                        .getModifiedCount();
            } catch (DuplicateKeyException exception) {
                skipped++;
            }
        }
        if (!articles.isEmpty()) {
            LOGGER.info("Article content-hash backfill: scanned={}, updated={}, duplicatesSkipped={}",
                    articles.size(), updated, skipped);
        }
    }
}
