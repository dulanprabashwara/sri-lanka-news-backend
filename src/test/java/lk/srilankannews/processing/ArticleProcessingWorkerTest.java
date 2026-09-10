package lk.srilankannews.processing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.processing.enrichment.ArticleEnrichmentService;
import lk.srilankannews.story.ArticleEmbeddingService;
import lk.srilankannews.story.StoryClusteringService;
import lk.srilankannews.translation.ArticleTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArticleProcessingWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");

    @Mock ArticleService articleService;
    @Mock ArticleEnrichmentService enrichmentService;
    @Mock ArticleEmbeddingService embeddingService;
    @Mock StoryClusteringService clusteringService;
    @Mock ArticleTranslationService translationService;
    private ArticleProcessingWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ArticleProcessingWorker(
                articleService, enrichmentService, embeddingService, clusteringService,
                translationService, null);
    }

    @Test
    void translatesBeforeEnrichmentAndCompletesDownstreamProcessing() {
        when(articleService.findById("article-1")).thenReturn(Optional.of(article()));
        when(enrichmentService.attempt("article-1"))
                .thenReturn(ArticleEnrichmentService.Outcome.SUCCEEDED);

        worker.process(event());

        var order = inOrder(
                translationService, enrichmentService, embeddingService, clusteringService);
        order.verify(translationService).ensureTranslations("article-1");
        order.verify(enrichmentService).attempt("article-1");
        order.verify(translationService).ensureTranslations("article-1");
        order.verify(embeddingService).ensureEmbedding("article-1");
        order.verify(clusteringService).cluster("article-1");
    }

    @Test
    void deferredEnrichmentDoesNotBlockEmbeddingOrClustering() {
        when(articleService.findById("article-1")).thenReturn(Optional.of(article()));
        when(enrichmentService.attempt("article-1"))
                .thenReturn(ArticleEnrichmentService.Outcome.DEFERRED);

        worker.process(event());

        verify(translationService).ensureTranslations("article-1");
        verify(embeddingService).ensureEmbedding("article-1");
        verify(clusteringService).cluster("article-1");
    }

    @Test
    void permanentEnrichmentFailureDoesNotBlockArticlePipeline() {
        when(articleService.findById("article-1")).thenReturn(Optional.of(article()));
        when(enrichmentService.attempt("article-1"))
                .thenReturn(ArticleEnrichmentService.Outcome.FAILED);

        worker.process(event());

        verify(embeddingService).ensureEmbedding("article-1");
        verify(clusteringService).cluster("article-1");
    }

    @Test
    void rateLimitDeferralAllowsPipelineToCompleteAndAssignStory() {
        when(articleService.findById("article-1")).thenReturn(Optional.of(article()));
        when(enrichmentService.attempt("article-1"))
                .thenReturn(ArticleEnrichmentService.Outcome.DEFERRED);
        when(clusteringService.cluster("article-1")).thenReturn("story-assigned-123");

        worker.process(event());

        verify(embeddingService).ensureEmbedding("article-1");
        verify(clusteringService).cluster("article-1");
    }

    @Test
    void actualEmbeddingFailureStillPropagatesToRedisRetryFlow() {
        when(articleService.findById("article-1")).thenReturn(Optional.of(article()));
        when(enrichmentService.attempt("article-1"))
                .thenReturn(ArticleEnrichmentService.Outcome.DEFERRED);
        org.mockito.Mockito.doThrow(new IllegalStateException("embedding unavailable"))
                .when(embeddingService).ensureEmbedding("article-1");

        assertThatThrownBy(() -> worker.process(event()))
                .isInstanceOf(IllegalStateException.class);
        verify(clusteringService, org.mockito.Mockito.never()).cluster("article-1");
    }

    private ArticleDiscoveredEvent event() {
        return new ArticleDiscoveredEvent(
                "event-1", "article-1", "source-1", NOW, 1, 1);
    }

    private Article article() {
        return new Article(
                "article-1", "source-1", "Headline", "https://example.com/1",
                "https://example.com/1", Language.EN, List.of(), NOW, NOW,
                ArticleCategory.OTHER,
                "Article content long enough to create an extractive summary and embedding.",
                "hash", ProcessingStatus.PENDING, NOW, NOW);
    }
}
