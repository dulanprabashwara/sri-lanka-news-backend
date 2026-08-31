package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

class StoryClusteringSelectionTest {
    private static final Instant NOW = StoryClusteringServiceTest.NOW;
    @Mock ArticleRepository articles;
    @Mock StoryRepository stories;
    @Mock StoryMatcher matcher;
    @Mock StoryPersistence persistence;
    @Mock ArticleStoryAssignment assignment;
    @Mock StoryClusterPartitionStore partitionStore;
    @Mock StoryClusteringTransactionExecutor transactionExecutor;
    StoryClusteringService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(transactionExecutor.execute(any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<String>) invocation.getArgument(0)).get());
        service = new StoryClusteringService(
                articles, stories, matcher, persistence, assignment,
                partitionStore, transactionExecutor,
                new StoryClusteringProperties(Duration.ofHours(48), 0.72, 200, 100),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void equalScoresPreferMostRecentStoryDeterministically() {
        Article article = StoryClusteringServiceTest.article("article-2", null, NOW);
        Story older = StoryClusteringServiceTest.story(
                "story-a", "rep-a", NOW.minusSeconds(600), 1);
        Story newer = StoryClusteringServiceTest.story(
                "story-b", "rep-b", NOW.minusSeconds(60), 1);
        Article repA = StoryClusteringServiceTest.article(
                "rep-a", "story-a", NOW.minusSeconds(600));
        Article repB = StoryClusteringServiceTest.article(
                "rep-b", "story-b", NOW.minusSeconds(60));
        when(articles.findById("article-2")).thenReturn(Optional.of(article));
        when(stories.findCandidates(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(older, newer));
        when(articles.findAllById(List.of("rep-a", "rep-b"))).thenReturn(List.of(repA, repB));
        when(matcher.score(article, repA)).thenReturn(0.80);
        when(matcher.score(article, repB)).thenReturn(0.80);
        when(assignment.assignIfAbsent("article-2", "story-b", NOW)).thenReturn("story-b");

        assertThat(service.cluster("article-2")).isEqualTo("story-b");
        verify(persistence).addArticleIfAbsent("story-b", article, NOW);
    }

    @Test
    void concurrentArticleAssignmentWinnerRemainsAuthoritative() {
        Article article = StoryClusteringServiceTest.article("article-1", null, NOW);
        Story created = StoryClusteringServiceTest.story("story-new", "article-1", NOW, 0);
        StoryPersistence.Creation creation = new StoryPersistence.Creation(created, true);
        when(articles.findById("article-1")).thenReturn(Optional.of(article));
        when(stories.findCandidates(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(persistence.createPending(article, NOW)).thenReturn(creation);
        when(assignment.assignIfAbsent("article-1", "story-new", NOW))
                .thenReturn("story-concurrent");

        assertThat(service.cluster("article-1")).isEqualTo("story-concurrent");
        verify(persistence).discardIfUnused(creation);
        verify(persistence).addArticleIfAbsent("story-concurrent", article, NOW);
        verify(persistence, org.mockito.Mockito.never()).addArticleIfAbsent(
                org.mockito.ArgumentMatchers.eq("story-new"), any(), any());
    }
}
