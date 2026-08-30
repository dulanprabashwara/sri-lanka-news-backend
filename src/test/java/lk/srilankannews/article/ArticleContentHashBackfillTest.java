package lk.srilankannews.article;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class ArticleContentHashBackfillTest {

    @Test
    void backfillsUniqueLegacyContentAndSkipsAnExistingDuplicateHash() throws Exception {
        MongoOperations mongo = org.mockito.Mockito.mock(MongoOperations.class);
        Document first = new Document("_id", "one").append("extractedContent", "Same content");
        Document second = new Document("_id", "two").append("extractedContent", "Same  content");
        when(mongo.find(any(Query.class), eq(Document.class), eq("articles")))
                .thenReturn(List.of(first, second));
        when(mongo.exists(any(Query.class), eq("articles"))).thenReturn(false, true);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq("articles")))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        new ArticleContentHashBackfill(mongo, new ArticleContentHasher())
                .run(new DefaultApplicationArguments());

        verify(mongo, times(1)).updateFirst(any(Query.class), any(Update.class), eq("articles"));
    }
}
