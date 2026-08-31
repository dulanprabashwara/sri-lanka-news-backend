package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

class StoryClusteringServiceTest {
    static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
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
    void createsStoryWhenNoCandidateMatches() {
        Article article = article("article-1", null, NOW);
        Story created = story("story-new", "article-1", NOW, 0);
        when(articles.findById("article-1")).thenReturn(Optional.of(article));
        when(stories.findCandidates(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(persistence.createPending(article, NOW))
                .thenReturn(new StoryPersistence.Creation(created, true));
        when(assignment.assignIfAbsent("article-1", "story-new", NOW)).thenReturn("story-new");

        assertThat(service.cluster("article-1")).isEqualTo("story-new");
        verify(persistence).addArticleIfAbsent("story-new", article, NOW);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(partitionStore, stories);
        order.verify(partitionStore).advance("hybrid-v1", NOW);
        order.verify(stories).findCandidates(any(), any(), any(Pageable.class));
    }

    @Test
    void replayWithStoryIdDoesNotSearchOrCreate() {
        Article article = article("article-1", "story-existing", NOW);
        when(articles.findById("article-1")).thenReturn(Optional.of(article));

        assertThat(service.cluster("article-1")).isEqualTo("story-existing");

        verify(persistence).addArticleIfAbsent("story-existing", article, NOW);
        verify(stories, never()).findCandidates(any(), any(), any(Pageable.class));
        verify(assignment, never()).assignIfAbsent(any(), any(), any());
    }

    static Article article(String id, String storyId, Instant publishedAt) {
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                "Summary", List.of("Election"), List.of(), List.of(), "model", "v1", NOW);
        return new Article(
                id, "source-" + id, "Election result announced", "https://example.com/" + id,
                "https://example.com/" + id, Language.EN, List.of(), publishedAt, NOW,
                ArticleCategory.POLITICS, "content", "hash-" + id, enrichment,
                ProcessingStatus.COMPLETED, storyId, NOW, NOW);
    }

    static Story story(String id, String representativeId, Instant publishedAt, long count) {
        return new Story(
                id, "Election result announced", representativeId, ArticleCategory.POLITICS,
                publishedAt, publishedAt, count, Set.of("source"), Set.of(),
                NOW, NOW, StoryMatcher.MATCHING_VERSION);
    }
}
