package lk.srilankannews.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import lk.srilankannews.ai.AiEntity;
import lk.srilankannews.ai.AiInput;
import lk.srilankannews.ai.AiInputPolicy;
import lk.srilankannews.ai.AiOutputValidator;
import lk.srilankannews.ai.AiProvider;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.AiResult;
import lk.srilankannews.ai.GeminiProperties;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.article.cache.ArticleFeedCache;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.story.ArticleEmbeddingService;
import lk.srilankannews.story.StoryClusteringService;
import lk.srilankannews.translation.ArticleTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArticleProcessingWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");

    @Mock
    private ArticleService articleService;
    @Mock
    private AiProvider aiProvider;
    @Mock
    private ArticleFeedCache feedCache;
    @Mock
    private ArticleEmbeddingService embeddingService;
    @Mock
    private StoryClusteringService clusteringService;
    @Mock
    private ArticleTranslationService translationService;
    private ArticleProcessingWorker worker;

    @BeforeEach
    void setUp() {
        GeminiProperties properties = properties();
        worker = new ArticleProcessingWorker(
                articleService,
                aiProvider,
                new AiInputPolicy(properties),
                new AiOutputValidator(),
                properties,
                feedCache,
                embeddingService,
                clusteringService,
                translationService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void persistsEnglishEnrichmentAndCompletesProcessing() {
        Article article = article(Language.EN, ProcessingStatus.PENDING, null);
        when(articleService.findById("article-1")).thenReturn(Optional.of(article));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article));
        when(aiProvider.enrich(any())).thenReturn(new AiResult(
                "A concise English summary.",
                ArticleCategory.POLITICS,
                List.of("Parliament"),
                List.of("budget"),
                List.of(new AiEntity("Sri Lanka", "LOCATION"))));
        when(articleService.completeEnrichment(
                org.mockito.ArgumentMatchers.eq("article-1"), any(), any()))
                .thenReturn(Optional.of(article));
        org.mockito.Mockito.doThrow(new IllegalStateException("cache unavailable"))
                .when(feedCache).invalidate();

        worker.process(event());

        ArgumentCaptor<AiInput> input = ArgumentCaptor.forClass(AiInput.class);
        verify(aiProvider).enrich(input.capture());
        assertThat(input.getValue().language()).isEqualTo(Language.EN);
        assertThat(input.getValue().content()).doesNotContain("GEMINI_API_KEY");

        ArgumentCaptor<ArticleAiEnrichment> enrichment =
                ArgumentCaptor.forClass(ArticleAiEnrichment.class);
        verify(articleService).completeEnrichment(
                org.mockito.ArgumentMatchers.eq("article-1"),
                enrichment.capture(),
                org.mockito.ArgumentMatchers.eq(ArticleCategory.POLITICS));
        assertThat(enrichment.getValue().summary()).isEqualTo("A concise English summary.");
        assertThat(enrichment.getValue().topics()).containsExactly("Parliament");
        assertThat(enrichment.getValue().keywords()).containsExactly("budget");
        assertThat(enrichment.getValue().entities().get(0).name()).isEqualTo("Sri Lanka");
        assertThat(enrichment.getValue().model()).isEqualTo("gemini-test");
        assertThat(enrichment.getValue().promptVersion()).isEqualTo("v1");
        assertThat(enrichment.getValue().processedAt()).isEqualTo(NOW);
        verify(feedCache).invalidate();
        org.mockito.InOrder processingOrder = org.mockito.Mockito.inOrder(
                articleService, embeddingService, clusteringService);
        processingOrder.verify(articleService).completeEnrichment(
                org.mockito.ArgumentMatchers.eq("article-1"), any(), any());
        processingOrder.verify(embeddingService).ensureEmbedding("article-1");
        processingOrder.verify(clusteringService).cluster("article-1");
        verify(translationService).ensureTranslations("article-1");
    }

    @Test
    void preservesSinhalaSummaryAndUnicode() {
        Article article = article(Language.SI, ProcessingStatus.PENDING, null);
        when(articleService.findById("article-1")).thenReturn(Optional.of(article));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article));
        when(aiProvider.enrich(any())).thenReturn(new AiResult(
                "??? ????? ?????????.",
                ArticleCategory.LOCAL,
                List.of("????? ?????"),
                List.of("?????"),
                List.of()));
        when(articleService.completeEnrichment(
                org.mockito.ArgumentMatchers.eq("article-1"), any(), any()))
                .thenReturn(Optional.of(article));
        org.mockito.Mockito.doThrow(new IllegalStateException("cache unavailable"))
                .when(feedCache).invalidate();

        worker.process(event());

        ArgumentCaptor<ArticleAiEnrichment> enrichment =
                ArgumentCaptor.forClass(ArticleAiEnrichment.class);
        verify(articleService).completeEnrichment(
                org.mockito.ArgumentMatchers.eq("article-1"),
                enrichment.capture(),
                org.mockito.ArgumentMatchers.eq(ArticleCategory.LOCAL));
        assertThat(enrichment.getValue().summary()).isEqualTo("??? ????? ?????????.");
        assertThat(enrichment.getValue().topics()).containsExactly("????? ?????");
    }

    @Test
    void currentModelAndPromptCompletionIsIdempotent() {
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                "Existing summary", List.of(), List.of(), List.of(),
                "gemini-test", "v1", NOW);
        when(articleService.findById("article-1")).thenReturn(Optional.of(
                article(Language.EN, ProcessingStatus.COMPLETED, enrichment)));

        worker.process(event());

        verify(aiProvider, never()).enrich(any());
        verify(articleService, never()).updateProcessingStatus(any(), any());
        verify(embeddingService).ensureEmbedding("article-1");
        verify(clusteringService).cluster("article-1");
        verify(translationService).ensureTranslations("article-1");
    }

    @Test
    void invalidProviderOutputFailsForExistingRetryFlow() {
        Article article = article(Language.EN, ProcessingStatus.PENDING, null);
        when(articleService.findById("article-1")).thenReturn(Optional.of(article));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article));
        when(aiProvider.enrich(any())).thenReturn(new AiResult(
                "", ArticleCategory.LOCAL, List.of(), List.of(), List.of()));

        assertThatThrownBy(() -> worker.process(event()))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).kind())
                .isEqualTo(AiProviderException.Kind.INVALID_RESPONSE);
        verify(articleService, never()).completeEnrichment(any(), any(), any());
        verify(feedCache, never()).invalidate();
    }

    @Test
    void providerFailurePropagatesForExistingRetryFlow() {
        Article article = article(Language.EN, ProcessingStatus.PENDING, null);
        when(articleService.findById("article-1")).thenReturn(Optional.of(article));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article));
        when(aiProvider.enrich(any())).thenThrow(new AiProviderException(
                AiProviderException.Kind.PROVIDER_FAILURE, "unavailable"));

        assertThatThrownBy(() -> worker.process(event()))
                .isInstanceOf(AiProviderException.class);
    }

    @Test
    void clusteringFailureRetriesWithoutCallingGeminiAgain() {
        Article pending = article(Language.EN, ProcessingStatus.PENDING, null);
        ArticleAiEnrichment existing = new ArticleAiEnrichment(
                "Existing summary", List.of(), List.of(), List.of(),
                "gemini-test", "v1", NOW);
        Article completed = article(Language.EN, ProcessingStatus.RETRYING, existing);
        when(articleService.findById("article-1"))
                .thenReturn(Optional.of(pending), Optional.of(completed));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(pending));
        when(aiProvider.enrich(any())).thenReturn(new AiResult(
                "Summary", ArticleCategory.LOCAL, List.of(), List.of(), List.of()));
        when(articleService.completeEnrichment(any(), any(), any()))
                .thenReturn(Optional.of(completed));
        org.mockito.Mockito.doThrow(new IllegalStateException("clustering failed"))
                .when(clusteringService).cluster("article-1");

        assertThatThrownBy(() -> worker.process(event()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> worker.process(event()))
                .isInstanceOf(IllegalStateException.class);

        verify(aiProvider, org.mockito.Mockito.times(1)).enrich(any());
        verify(articleService, org.mockito.Mockito.times(1))
                .completeEnrichment(any(), any(), any());
        verify(embeddingService, org.mockito.Mockito.times(2)).ensureEmbedding("article-1");
        verify(clusteringService, org.mockito.Mockito.times(2)).cluster("article-1");
    }

    @Test
    void embeddingFailurePreservesEnrichmentAndRetrySkipsGemini() {
        Article pending = article(Language.EN, ProcessingStatus.PENDING, null);
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                "Persisted summary", List.of("topic"), List.of(), List.of(),
                "gemini-test", "v1", NOW);
        Article completed = article(Language.EN, ProcessingStatus.COMPLETED, enrichment);
        when(articleService.findById("article-1"))
                .thenReturn(Optional.of(pending), Optional.of(completed));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(pending));
        when(aiProvider.enrich(any())).thenReturn(new AiResult(
                "Persisted summary", ArticleCategory.LOCAL,
                List.of("topic"), List.of(), List.of()));
        when(articleService.completeEnrichment(any(), any(), any()))
                .thenReturn(Optional.of(completed));
        org.mockito.Mockito.doThrow(new IllegalStateException("embedding unavailable"))
                .when(embeddingService).ensureEmbedding("article-1");

        assertThatThrownBy(() -> worker.process(event()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> worker.process(event()))
                .isInstanceOf(IllegalStateException.class);

        verify(aiProvider, org.mockito.Mockito.times(1)).enrich(any());
        verify(articleService, org.mockito.Mockito.times(1))
                .completeEnrichment(any(), any(), any());
        verify(embeddingService, org.mockito.Mockito.times(2)).ensureEmbedding("article-1");
        verify(clusteringService, never()).cluster(any());
    }

    private ArticleDiscoveredEvent event() {
        return new ArticleDiscoveredEvent(
                "event-1", "article-1", "source-1", NOW, 1, 1);
    }

    private GeminiProperties properties() {
        return new GeminiProperties(
                "test-key", "gemini-test", "v1", Duration.ofSeconds(5), 30000);
    }

    private Article article(
            Language language, ProcessingStatus status, ArticleAiEnrichment enrichment) {
        String content = "This fixture article content is deliberately long enough "
                + "for deterministic AI processing in the unit test.";
        return new Article(
                "article-1", "source-1", "Headline", "https://example.com/1",
                "https://example.com/1", language, List.of(), NOW, NOW,
                ArticleCategory.OTHER, content, "hash", enrichment, status, NOW, NOW);
    }
}
