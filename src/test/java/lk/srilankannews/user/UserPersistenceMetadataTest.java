package lk.srilankannews.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;

class UserPersistenceMetadataTest {
    @Test
    void preferencesHaveUniqueUserIndex() throws Exception {
        Indexed indexed = UserPreferences.class.getDeclaredField("userId").getAnnotation(Indexed.class);
        assertThat(indexed).isNotNull();
        assertThat(indexed.unique()).isTrue();
    }

    @Test
    void bookmarksHaveUniqueTargetAndOwnerListingIndexes() {
        CompoundIndexes indexes = UserBookmark.class.getAnnotation(CompoundIndexes.class);
        assertThat(Arrays.stream(indexes.value()).map(index -> index.name()).toList())
                .containsExactlyInAnyOrder("uk_user_bookmark_target", "idx_user_bookmarks_created");
        assertThat(Arrays.stream(indexes.value())
                .filter(index -> index.name().equals("uk_user_bookmark_target"))
                .findFirst().orElseThrow().unique()).isTrue();
    }
}
