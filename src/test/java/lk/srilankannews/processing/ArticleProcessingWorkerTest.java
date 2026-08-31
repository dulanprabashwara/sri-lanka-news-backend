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
