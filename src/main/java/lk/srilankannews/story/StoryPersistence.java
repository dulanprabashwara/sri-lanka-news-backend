package lk.srilankannews.story;

import java.time.Instant;
import lk.srilankannews.article.Article;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
class StoryPersistence {
    private final StoryRepository repository;
    private final MongoOperations mongo;

    StoryPersistence(StoryRepository repository, MongoOperations mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    Creation createPending(Article article, Instant now) {
        try {
            return new Creation(
                    repository.save(Story.pending(article, now, StoryMatcher.MATCHING_VERSION)),
                    true);
        } catch (DuplicateKeyException exception) {
            Story existing = repository.findByRepresentativeArticleId(article.id())
                    .orElseThrow(() -> exception);
            return new Creation(existing, false);
        }
    }

    boolean addArticleIfAbsent(String storyId, Article article, Instant now) {
        Query missingArticle = Query.query(Criteria.where("_id").is(storyId)
                .and("articleIds").ne(article.id()));
        Update update = new Update()
                .addToSet("articleIds", article.id())
                .addToSet("sourceIds", article.sourceId())
                .inc("articleCount", 1)
                .min("firstPublishedAt", article.publishedAt())
                .max("lastPublishedAt", article.publishedAt())
                .set("updatedAt", now);
        return mongo.updateFirst(missingArticle, update, Story.class).getModifiedCount() == 1;
    }

    void discardIfUnused(Creation creation) {
        if (!creation.created()) {
            return;
        }
        Query unused = Query.query(Criteria.where("_id").is(creation.story().id())
                .and("articleCount").is(0));
        mongo.remove(unused, Story.class);
    }

    record Creation(Story story, boolean created) {
    }
}
