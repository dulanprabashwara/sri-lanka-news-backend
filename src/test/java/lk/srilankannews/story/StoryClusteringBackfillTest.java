package lk.srilankannews.story;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.processing.ArticleDiscoveredNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.domain.Pageable;

class StoryClusteringBackfillTest {

    @Test
    void processesOnlyRepositoryBoundedEnrichedUnassignedArticles() throws Exception {
        ArticleRepository articles = org.mockito.Mockito.mock(ArticleRepository.class);
        StoryClusteringService clustering = org.mockito.Mockito.mock(StoryClusteringService.class);
        ArticleDiscoveredNotifier notifier = org.mockito.Mockito.mock(ArticleDiscoveredNotifier.class);
        var properties = new StoryClusteringProperties(Duration.ofHours(48), 0.72, 200, 2);
        var first = StoryClusteringServiceTest.article(
                "article-1", null, StoryClusteringServiceTest.NOW);
        var second = StoryClusteringServiceTest.article(
                "article-2", null, StoryClusteringServiceTest.NOW);
        when(articles.findEnrichedWithoutStory(
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(first, second));

        new StoryClusteringBackfill(articles, clustering, properties, notifier)
                .run(new DefaultApplicationArguments());

        verify(clustering).cluster("article-1");
        verify(clustering).cluster("article-2");
    }

    @Test
    void failedBackfillUsesExistingEventRetryBoundary() throws Exception {
        ArticleRepository articles = org.mockito.Mockito.mock(ArticleRepository.class);
        StoryClusteringService clustering = org.mockito.Mockito.mock(StoryClusteringService.class);
        ArticleDiscoveredNotifier notifier = org.mockito.Mockito.mock(ArticleDiscoveredNotifier.class);
        var properties = new StoryClusteringProperties(Duration.ofHours(48), 0.72, 200, 1);
        var article = StoryClusteringServiceTest.article(
                "article-1", null, StoryClusteringServiceTest.NOW);
        when(articles.findEnrichedWithoutStory(
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(article));
        doThrow(new IllegalStateException("failed")).when(clustering).cluster("article-1");

        new StoryClusteringBackfill(articles, clustering, properties, notifier)
                .run(new DefaultApplicationArguments());

        verify(notifier).notifyDiscovered(article);
    }
}
