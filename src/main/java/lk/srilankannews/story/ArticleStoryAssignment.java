package lk.srilankannews.story;

import java.time.Instant;
import lk.srilankannews.article.Article;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
class ArticleStoryAssignment {
    private final MongoOperations mongo;

    ArticleStoryAssignment(MongoOperations mongo) {
        this.mongo = mongo;
    }

    String assignIfAbsent(String articleId, String storyId, Instant now) {
        Query unassigned = Query.query(Criteria.where("_id").is(articleId)
                .and("storyId").is(null));
        long modified = mongo.updateFirst(
                unassigned,
                new Update().set("storyId", storyId).set("updatedAt", now),
                Article.class).getModifiedCount();
        if (modified == 1) {
            return storyId;
        }

        Article current = mongo.findById(articleId, Article.class);
        if (current == null) {
            throw new IllegalStateException("Article disappeared during Story assignment");
        }
        if (current.storyId() == null) {
            throw new IllegalStateException("Article Story assignment did not complete");
        }
        return current.storyId();
    }
}
