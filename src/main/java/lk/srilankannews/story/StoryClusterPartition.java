package lk.srilankannews.story;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "story_cluster_partitions")
record StoryClusterPartition(
        @Id String id,
        long revision,
        Instant updatedAt) {

    static String activeId() {
        return StoryMatcher.MATCHING_VERSION;
    }
}
