package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import lk.srilankannews.article.ArticleCategory;
import org.junit.jupiter.api.Test;

class StoryPublicVisibilityTest {

    @Test
    void requiresAtLeastTwoReportsAndTwoDistinctPublishers() {
        assertThat(story(1, Set.of("source-1")).isPubliclyVisible()).isFalse();
        assertThat(story(2, Set.of("source-1")).isPubliclyVisible()).isFalse();
        assertThat(story(1, Set.of("source-1", "source-2")).isPubliclyVisible()).isFalse();
        assertThat(story(2, Set.of("source-1", "source-2")).isPubliclyVisible()).isTrue();
    }

    private Story story(long articleCount, Set<String> sourceIds) {
        Instant now = Instant.parse("2026-09-10T00:00:00Z");
        return new Story(
                "story-1", "Story", "article-1", ArticleCategory.LOCAL,
                now, now, articleCount, sourceIds, Set.of("article-1"),
                now, now, StoryMatcher.MATCHING_VERSION);
    }
}
