package lk.srilankannews.processing.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
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
import lk.srilankannews.ai.AiInputPolicy;
import lk.srilankannews.ai.AiOutputValidator;
import lk.srilankannews.ai.AiProvider;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.AiResult;
import lk.srilankannews.ai.GeminiProperties;
import lk.srilankannews.ai.openrouter.OpenRouterAiProvider;
import lk.srilankannews.ai.openrouter.OpenRouterProperties;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.article.cache.ArticleFeedCache;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArticleEnrichmentServiceOpenRouterTest {
    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");

    private ArticleService articleService;
    private AiProvider geminiProvider;
    private OpenRouterAiProvider openRouterProvider;
    private ArticleFeedCache feedCache;
    private ArticleEnrichmentJobStore jobs;
    private GeminiRequestController requestController;
    private OpenRouterProperties openRouterProperties;
    private ArticleEnrichmentService service;

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        geminiProvider = mock(AiProvider.class);
        openRouterProvider = mock(OpenRouterAiProvider.class);
        feedCache = mock(ArticleFeedCache.class);
        jobs = mock(ArticleEnrichmentJobStore.class);

        GeminiProperties gemini = new GeminiProperties(
                "gemini-key", "gemini-2.5", "v1", Duration.ofSeconds(5), 30000);
        openRouterProperties = new OpenRouterProperties(
                "openrouter-key", "openrouter/free", "https://openrouter.ai/api/v1", Duration.ofSeconds(10));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        requestController = new GeminiRequestController(
                new GeminiBackgroundProperties(1, Duration.ZERO, Duration.ofMinutes(1)), clock);

        service = new ArticleEnrichmentService(
                articleService, geminiProvider, new AiInputPolicy(gemini),
                new AiOutputValidator(), gemini, feedCache, jobs,
                new EnrichmentRetryProperties(true, 5, 5, Duration.ofMinutes(5), Duration.ofHours(6),
                        Duration.ofMinutes(2)),
                requestController, clock, openRouterProvider, openRouterProperties);
    }

    @Test
    void geminiSuccessDoesNotCallOpenRouter() {
        arrangeClaim(1);
        when(geminiProvider.enrich(any())).thenReturn(result("GEMINI", "gemini-2.5"));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));
        when(articleService.completeEnrichment(any(), any(), any()))
                .thenReturn(Optional.of(article()));

        ArticleEnrichmentService.Outcome outcome = service.attempt("article-1");

        assertThat(outcome).isEqualTo(ArticleEnrichmentService.Outcome.SUCCEEDED);
        verify(openRouterProvider, never()).enrich(any());
        verify(articleService).completeEnrichment(
                argThat(id -> id.equals("article-1")),
                argThat(enrichment -> enrichment.model().equals("gemini-2.5")),
                any());
    }

    @Test
    void gemini429FallsBackToOpenRouterSuccess() {
        arrangeClaim(1);
        when(geminiProvider.enrich(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota",
                "gemini-2.5", Duration.ofSeconds(60), null));
        when(openRouterProvider.enrich(any())).thenReturn(result("OPENROUTER", "openrouter/free"));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));
        when(articleService.completeEnrichment(any(), any(), any()))
                .thenReturn(Optional.of(article()));

        ArticleEnrichmentService.Outcome outcome = service.attempt("article-1");

        assertThat(outcome).isEqualTo(ArticleEnrichmentService.Outcome.SUCCEEDED);
        verify(openRouterProvider).enrich(any());
        verify(articleService).completeEnrichment(
                argThat(id -> id.equals("article-1")),
                argThat(enrichment -> enrichment.model().equals("openrouter/free")),
                any());
        verify(jobs).markSucceeded("article-1", "claim-1", NOW);
        assertThat(requestController.isCoolingDown()).isTrue();
    }

    @Test
    void geminiTimeoutFallsBackToOpenRouterSuccess() {
        arrangeClaim(1);
        when(geminiProvider.enrich(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.TIMEOUT_NETWORK, "Timeout", null, "TIMEOUT", "Timeout",
                "gemini-2.5", null, null));
        when(openRouterProvider.enrich(any())).thenReturn(result("OPENROUTER", "openrouter/free"));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));
        when(articleService.completeEnrichment(any(), any(), any()))
                .thenReturn(Optional.of(article()));

        ArticleEnrichmentService.Outcome outcome = service.attempt("article-1");

        assertThat(outcome).isEqualTo(ArticleEnrichmentService.Outcome.SUCCEEDED);
        verify(openRouterProvider).enrich(any());
        verify(jobs).markSucceeded("article-1", "claim-1", NOW);
    }

    @Test
    void bothFailEnrichmentDeferred() {
        arrangeClaim(1);
        when(geminiProvider.enrich(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota",
                "gemini-2.5", Duration.ofSeconds(45), null));
        when(openRouterProvider.enrich(any())).thenThrow(new AiProviderException(
                "OPENROUTER", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota",
                "openrouter/free", null, null));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));

        ArticleEnrichmentService.Outcome outcome = service.attempt("article-1");

        assertThat(outcome).isEqualTo(ArticleEnrichmentService.Outcome.DEFERRED);
        verify(jobs).markDeferred("article-1", "claim-1", "RATE_LIMIT", NOW.plus(Duration.ofSeconds(45)), NOW);
    }

    @Test
    void malformedOpenRouterEnrichmentDeferred() {
        arrangeClaim(1);
        when(geminiProvider.enrich(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota",
                "gemini-2.5", null, null));
        when(openRouterProvider.enrich(any())).thenThrow(new AiProviderException(
                "OPENROUTER", AiProviderException.Kind.INVALID_RESPONSE, "Malformed JSON", null,
                "MALFORMED", "Malformed", "openrouter/free", null, null));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));

        ArticleEnrichmentService.Outcome outcome = service.attempt("article-1");

        assertThat(outcome).isEqualTo(ArticleEnrichmentService.Outcome.DEFERRED);
        verify(articleService, never()).completeEnrichment(any(), any(), any());
        verify(jobs).markDeferred(org.mockito.ArgumentMatchers.eq("article-1"),
                org.mockito.ArgumentMatchers.eq("claim-1"),
                org.mockito.ArgumentMatchers.eq("RATE_LIMIT"), any(),
                org.mockito.ArgumentMatchers.eq(NOW));
    }

    @Test
    void deferredRetryCanUseOpenRouterWhenGeminiIsCoolingDown() {
        arrangeClaim(2);
        requestController.recordRateLimit(Duration.ofMinutes(5));
        assertThat(requestController.isCoolingDown()).isTrue();

        when(openRouterProvider.enrich(any())).thenReturn(result("OPENROUTER", "openrouter/free"));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));
        when(articleService.completeEnrichment(any(), any(), any()))
                .thenReturn(Optional.of(article()));

        ArticleEnrichmentService.Outcome outcome = service.attempt("article-1");

        assertThat(outcome).isEqualTo(ArticleEnrichmentService.Outcome.SUCCEEDED);
        verify(geminiProvider, never()).enrich(any());
        verify(openRouterProvider).enrich(any());
        verify(jobs).markSucceeded("article-1", "claim-1", NOW);
    }

    @Test
    void missingOpenRouterKeyDefersWithoutOpenRouter() {
        OpenRouterProperties unconfigured = new OpenRouterProperties(
                "", "openrouter/free", "https://openrouter.ai/api/v1", Duration.ofSeconds(10));
        GeminiProperties gemini = new GeminiProperties(
                "gemini-key", "gemini-2.5", "v1", Duration.ofSeconds(5), 30000);
        ArticleEnrichmentService unconfiguredService = new ArticleEnrichmentService(
                articleService, geminiProvider, new AiInputPolicy(gemini),
                new AiOutputValidator(), gemini, feedCache, jobs,
                new EnrichmentRetryProperties(true, 5, 5, Duration.ofMinutes(5), Duration.ofHours(6),
                        Duration.ofMinutes(2)),
                requestController, Clock.fixed(NOW, ZoneOffset.UTC), openRouterProvider, unconfigured);

        arrangeClaim(1);
        when(geminiProvider.enrich(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota",
                "gemini-2.5", null, null));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));

        ArticleEnrichmentService.Outcome outcome = unconfiguredService.attempt("article-1");

        assertThat(outcome).isEqualTo(ArticleEnrichmentService.Outcome.DEFERRED);
        verify(openRouterProvider, never()).enrich(any());
    }

    private void arrangeClaim(int attempt) {
        when(articleService.findById("article-1")).thenReturn(Optional.of(article()));
        when(jobs.claim("article-1", NOW, Duration.ofMinutes(2), 5))
                .thenReturn(Optional.of(new ArticleEnrichmentJob(
                        "article-1", EnrichmentStatus.PROCESSING, attempt, null,
                        NOW.plus(Duration.ofMinutes(2)), "claim-1", null, NOW)));
    }

    private Article article() {
        return new Article(
                "article-1", "source-1", "Headline", "https://example.com/1",
                "https://example.com/1", Language.EN, List.of(), NOW, NOW,
                ArticleCategory.OTHER,
                "This article contains enough meaningful text for enrichment processing.",
                "hash", ProcessingStatus.PENDING, NOW, NOW);
    }

    private AiResult result(String provider, String model) {
        return new AiResult(
                "Summary of the article",
                ArticleCategory.LOCAL,
                List.of("Sri Lanka"),
                List.of("news"),
                List.of(new AiEntity("Colombo", "LOCATION")),
                provider,
                model);
    }
}
