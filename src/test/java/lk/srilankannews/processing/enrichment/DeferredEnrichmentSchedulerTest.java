package lk.srilankannews.processing.enrichment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lk.srilankannews.story.ArticleEmbeddingService;
import lk.srilankannews.story.StoryClusteringService;
import lk.srilankannews.translation.ArticleTranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeferredEnrichmentSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");

    @Mock ArticleEnrichmentJobStore jobs;
    @Mock ArticleEnrichmentService enrichmentService;
    @Mock ArticleTranslationService translationService;
    @Mock ArticleEmbeddingService embeddingService;
    @Mock StoryClusteringService clusteringService;

    @Test
    void retriesDueBatchAndRefreshesTranslationAndStoryInputsAfterSuccess() {
        EnrichmentRetryProperties properties = new EnrichmentRetryProperties(
                true, 3, 5, Duration.ofMinutes(5), Duration.ofHours(6),
                Duration.ofMinutes(2));
        when(jobs.findDueArticleIds(NOW, 5, 3)).thenReturn(List.of("article-1"));
        when(enrichmentService.attempt("article-1"))
                .thenReturn(ArticleEnrichmentService.Outcome.SUCCEEDED);
        DeferredEnrichmentScheduler scheduler = new DeferredEnrichmentScheduler(
                jobs, enrichmentService, translationService, embeddingService,
                clusteringService, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.retryDue();

        verify(enrichmentService).attempt("article-1");
        verify(translationService).ensureTranslations("article-1");
        verify(embeddingService).ensureEmbedding("article-1");
        verify(clusteringService).cluster("article-1");
    }

    @Test
    void retriesDueBatchWhenDeferredAgainDoesNotTriggerDownstream() {
        EnrichmentRetryProperties properties = new EnrichmentRetryProperties(
                true, 3, 5, Duration.ofMinutes(5), Duration.ofHours(6),
                Duration.ofMinutes(2));
        when(jobs.findDueArticleIds(NOW, 5, 3)).thenReturn(List.of("article-1"));
        when(enrichmentService.attempt("article-1"))
                .thenReturn(ArticleEnrichmentService.Outcome.DEFERRED);
        DeferredEnrichmentScheduler scheduler = new DeferredEnrichmentScheduler(
                jobs, enrichmentService, translationService, embeddingService,
                clusteringService, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.retryDue();

        verify(enrichmentService).attempt("article-1");
        org.mockito.Mockito.verifyNoInteractions(translationService, embeddingService, clusteringService);
    }

    @Test
    void schedulerCanLaterClaimAndRetryDeferredJob() {
        EnrichmentRetryProperties properties = new EnrichmentRetryProperties(
                true, 5, 10, Duration.ofMinutes(1), Duration.ofHours(1),
                Duration.ofMinutes(2));
        when(jobs.findDueArticleIds(NOW, 10, 5)).thenReturn(List.of("article-deferred-99"));
        when(enrichmentService.attempt("article-deferred-99"))
                .thenReturn(ArticleEnrichmentService.Outcome.SUCCEEDED);
        DeferredEnrichmentScheduler scheduler = new DeferredEnrichmentScheduler(
                jobs, enrichmentService, translationService, embeddingService,
                clusteringService, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.retryDue();

        verify(enrichmentService).attempt("article-deferred-99");
        verify(translationService).ensureTranslations("article-deferred-99");
        verify(embeddingService).ensureEmbedding("article-deferred-99");
        verify(clusteringService).cluster("article-deferred-99");
    }
}
