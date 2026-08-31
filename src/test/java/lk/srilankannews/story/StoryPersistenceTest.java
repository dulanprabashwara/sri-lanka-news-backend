package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class StoryPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");

    @Test
    void membershipUpdateIsGuardedByArticleIdForReplaySafety() {
        StoryRepository repository = org.mockito.Mockito.mock(StoryRepository.class);
        MongoOperations mongo = org.mockito.Mockito.mock(MongoOperations.class);
        StoryPersistence persistence = new StoryPersistence(repository, mongo);
        when(mongo.updateFirst(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(
                        UpdateResult.acknowledged(1, 1L, null),
                        UpdateResult.acknowledged(1, 0L, null));

        assertThat(persistence.addArticleIfAbsent("story-1", article(), NOW)).isTrue();
        assertThat(persistence.addArticleIfAbsent("story-1", article(), NOW)).isFalse();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo, org.mockito.Mockito.times(2)).updateFirst(
                query.capture(), update.capture(), any(Class.class));
        assertThat(query.getValue().getQueryObject().toJson())
                .contains("articleIds").contains("article-1");
        org.bson.Document updateObject = update.getValue().getUpdateObject();
        assertThat(updateObject.keySet()).contains("$addToSet", "$inc", "$min", "$max", "$set");
        assertThat(updateObject.get("$addToSet", org.bson.Document.class).keySet())
                .contains("sourceIds", "articleIds");
        assertThat(updateObject.get("$inc", org.bson.Document.class).keySet())
                .contains("articleCount");
    }

    private Article article() {
        return new Article(
                "article-1", "source-1", "Title", "https://example.com/1",
                "https://example.com/1", Language.EN, List.of(), NOW, NOW,
                ArticleCategory.LOCAL, "content", "hash", ProcessingStatus.COMPLETED,
                NOW, NOW);
    }
}
