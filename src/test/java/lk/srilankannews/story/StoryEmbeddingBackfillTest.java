package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.domain.Pageable;

class StoryEmbeddingBackfillTest {
    private static final Instant NOW = Instant.parse("2026-08-31T06:00:00Z");

    @Mock
    private ArticleRepository repository;
    @Mock
    private ArticleEmbeddingService embeddingService;
    @Mock
    private StoryClusteringService clusteringService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void appliesBoundedBackfillAndClustersOnlyUnassignedArticle() throws Exception {
        StoryEmbeddingProperties properties = properties(2);
        Article unassigned = article("article-1", null);
        Article assigned = article("article-2", "story-2");
        when(repository.findEmbeddingBackfillCandidates(
                eq(properties.model()), eq(properties.dimensions()),
                eq(properties.inputVersion()), any()))
                .thenReturn(List.of(unassigned, assigned));
        when(embeddingService.ensureEmbedding("article-1")).thenReturn(unassigned);
        when(embeddingService.ensureEmbedding("article-2")).thenReturn(assigned);

        new StoryEmbeddingBackfill(
                repository, embeddingService, clusteringService, properties)
                .run(new DefaultApplicationArguments());

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findEmbeddingBackfillCandidates(
                eq(properties.model()), eq(properties.dimensions()),
                eq(properties.inputVersion()), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(2);
        verify(clusteringService).cluster("article-1");
        verify(clusteringService, never()).cluster("article-2");
    }

    @Test
    void zeroLimitDisablesBackfill() throws Exception {
        new StoryEmbeddingBackfill(
                repository, embeddingService, clusteringService, properties(0))
                .run(new DefaultApplicationArguments());

        verify(repository, never()).findEmbeddingBackfillCandidates(
                any(), anyInt(), any(), any());
        verify(embeddingService, never()).ensureEmbedding(any());
    }

    private StoryEmbeddingProperties properties(int limit) {
        return new StoryEmbeddingProperties(
                "gemini-embedding-2", 768, "story-semantic-v2", 8000,
                0.82, 0.90, limit);
    }

    private Article article(String id, String storyId) {
        return new Article(
                id, "source-1", "Title", "https://example.com/" + id,
                "https://example.com/" + id, Language.EN, List.of(), NOW, NOW,
                ArticleCategory.LOCAL, "content", "hash-" + id, null,
                ProcessingStatus.COMPLETED, storyId, NOW, NOW);
    }
}
