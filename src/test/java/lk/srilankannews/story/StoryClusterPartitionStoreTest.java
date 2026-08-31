package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class StoryClusterPartitionStoreTest {

    @Test
    void atomicallyUpsertsAndAdvancesPartitionRevision() {
        MongoOperations mongo = org.mockito.Mockito.mock(MongoOperations.class);
        StoryClusterPartitionStore store = new StoryClusterPartitionStore(mongo);
        Instant now = Instant.parse("2026-08-31T00:00:00Z");

        store.advance("hybrid-v1", now);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).upsert(
                query.capture(), update.capture(), eq(StoryClusterPartition.class));
        assertThat(query.getValue().getQueryObject().getString("_id"))
                .isEqualTo("hybrid-v1");
        Document updateObject = update.getValue().getUpdateObject();
        assertThat(updateObject.get("$inc", Document.class).get("revision"))
                .isEqualTo(1);
        assertThat(updateObject.get("$set", Document.class).get("updatedAt"))
                .isEqualTo(now);
    }
}
