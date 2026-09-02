package lk.srilankannews.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

class UserFollowPersistenceMetadataTest {
    @Test
    void declaresPrivateCollectionAndRequiredIndexes() {
        assertThat(UserFollow.class.getAnnotation(Document.class).collection())
                .isEqualTo("user_follows");
        CompoundIndexes indexes = UserFollow.class.getAnnotation(CompoundIndexes.class);
        assertThat(Arrays.stream(indexes.value()).map(index -> index.name()).toList())
                .containsExactlyInAnyOrder("uk_user_follow_target", "idx_user_follows_created");
        assertThat(Arrays.stream(indexes.value())
                .filter(index -> index.name().equals("uk_user_follow_target"))
                .findFirst().orElseThrow().unique()).isTrue();
    }
}
