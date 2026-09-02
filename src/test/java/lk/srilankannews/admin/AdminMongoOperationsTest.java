package lk.srilankannews.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ProcessingStatus;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class AdminMongoOperationsTest {

    @Mock MongoOperations mongo;

    @Test
    void appliesStatusSourceLimitAndNewestDiscoverySort() {
        when(mongo.find(any(Query.class), eq(Article.class))).thenReturn(List.of());
        AdminMongoOperations operations = new AdminMongoOperations(mongo);

        operations.findArticles(ProcessingStatus.FAILED, "source-1", 25);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(captor.capture(), eq(Article.class));
        assertThat(captor.getValue().getQueryObject())
                .containsEntry("processingStatus", ProcessingStatus.FAILED)
                .containsEntry("sourceId", "source-1");
        assertThat(captor.getValue().getLimit()).isEqualTo(25);
        assertThat(captor.getValue().getSortObject())
                .containsEntry("discoveredAt", -1)
                .containsEntry("id", -1);
    }

    @Test
    void retryClaimRequiresFailedStatusAndSetsRetryingAtomically() {
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        AdminMongoOperations operations = new AdminMongoOperations(mongo);

        operations.claimFailedForRetry("507f1f77bcf86cd799439011", now);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(query.capture(), update.capture(),
                any(FindAndModifyOptions.class), eq(Article.class));
        assertThat(query.getValue().getQueryObject())
                .containsEntry("_id", "507f1f77bcf86cd799439011")
                .containsEntry("processingStatus", ProcessingStatus.FAILED);
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set)
                .containsEntry("processingStatus", ProcessingStatus.RETRYING)
                .containsEntry("updatedAt", now);
    }
}
