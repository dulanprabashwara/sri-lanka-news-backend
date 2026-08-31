package lk.srilankannews.story;

import java.time.Instant;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
class StoryClusterPartitionStore {
    private final MongoOperations mongo;

    StoryClusterPartitionStore(MongoOperations mongo) {
        this.mongo = mongo;
    }

    void ensureExists(String partitionId, Instant now) {
        Query partition = Query.query(Criteria.where("_id").is(partitionId));
        Update initial = new Update()
                .setOnInsert("revision", 0)
                .setOnInsert("updatedAt", now);
        mongo.upsert(partition, initial, StoryClusterPartition.class);
    }

    void advance(String partitionId, Instant now) {
        Query partition = Query.query(Criteria.where("_id").is(partitionId));
        Update update = new Update()
                .inc("revision", 1)
                .set("updatedAt", now);
        mongo.upsert(partition, update, StoryClusterPartition.class);
    }
}
