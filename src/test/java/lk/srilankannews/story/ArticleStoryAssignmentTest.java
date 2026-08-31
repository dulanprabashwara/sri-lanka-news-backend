package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import lk.srilankannews.article.Article;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class ArticleStoryAssignmentTest {

    @Test
    void concurrentWinnerIsReturnedWhenArticleWasAlreadyClaimed() {
        MongoOperations mongo = org.mockito.Mockito.mock(MongoOperations.class);
        ArticleStoryAssignment assignment = new ArticleStoryAssignment(mongo);
        Article assigned = new Article(
                "article-1", "source-1", "title", "url", "canonical", null,
                java.util.List.of(), Instant.EPOCH, Instant.EPOCH, null, "content",
                "hash", null, null, "story-winner", Instant.EPOCH, Instant.EPOCH);
        when(mongo.updateFirst(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        when(mongo.findById("article-1", Article.class)).thenReturn(assigned);

        assertThat(assignment.assignIfAbsent(
                "article-1", "story-loser", Instant.EPOCH)).isEqualTo("story-winner");
    }
}
