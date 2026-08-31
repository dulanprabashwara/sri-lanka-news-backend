package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StoryClusteringConcurrencyTest {

    @Test
    void relatedCrossLanguageArticlesConcurrentlyCreateOneStory() throws Exception {
        StoryConcurrencyFixture fixture = new StoryConcurrencyFixture();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> fixture.service.cluster("article-a"));
            var second = pool.submit(() -> fixture.service.cluster("article-b"));

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("story-1");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("story-1");
        } finally {
            pool.shutdownNow();
        }

        assertThat(fixture.storyCreates).hasValue(1);
        assertThat(fixture.storedArticles.get("article-a").storyId()).isEqualTo("story-1");
        assertThat(fixture.storedArticles.get("article-b").storyId()).isEqualTo("story-1");
        assertThat(fixture.storedStory.get().articleCount()).isEqualTo(2);
        assertThat(fixture.storedStory.get().articleIds())
                .containsExactlyInAnyOrder("article-a", "article-b");
        org.mockito.Mockito.verify(fixture.partitions, org.mockito.Mockito.times(2))
                .advance("hybrid-v1", StoryConcurrencyFixture.NOW);
    }
}
