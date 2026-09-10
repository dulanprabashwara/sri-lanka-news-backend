package lk.srilankannews.processing.enrichment;

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
import lk.srilankannews.ai.AiInputPolicy;
import lk.srilankannews.ai.AiOutputValidator;
import lk.srilankannews.ai.AiProvider;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.AiResult;
import lk.srilankannews.ai.GeminiProperties;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.article.cache.ArticleFeedCache;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArticleEnrichmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");

    @Mock ArticleService articleService;
    @Mock AiProvider provider;
    @Mock ArticleFeedCache feedCache;
    @Mock ArticleEnrichmentJobStore jobs;
    private ArticleEnrichmentService service;

    @BeforeEach
    void setUp() {
        GeminiProperties gemini = new GeminiProperties(
                "test-key", "gemini-test", "v1", Duration.ofSeconds(5), 30000);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ArticleEnrichmentService(
                articleService, provider, new AiInputPolicy(gemini),
                new AiOutputValidator(), gemini, feedCache, jobs,
                retryProperties(), new GeminiRequestController(
                        new GeminiBackgroundProperties(1, Duration.ZERO, Duration.ofMinutes(1)),
                        clock), clock);
    }

    @Test
    void successfulEnrichmentIsPersistedAndMarkedSucceeded() {
        arrangeClaim();
        when(provider.enrich(any())).thenReturn(result());
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));
        when(articleService.completeEnrichment(any(), any(), any()))
                .thenReturn(Optional.of(article()));

        assertThat(service.attempt("article-1"))
                .isEqualTo(ArticleEnrichmentService.Outcome.SUCCEEDED);
        verify(articleService).completeEnrichment(any(), any(), any());
        verify(jobs).markSucceeded("article-1", "claim-1", NOW);
    }

    @Test
    void rateLimitIsDeferredWithoutEscaping() {
        arrangeClaim();
        when(provider.enrich(any())).thenThrow(new AiProviderException(
                AiProviderException.Kind.RATE_LIMIT, "limited"));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));

        assertThat(service.attempt("article-1"))
                .isEqualTo(ArticleEnrichmentService.Outcome.DEFERRED);
        verify(jobs).markDeferred(
                org.mockito.ArgumentMatchers.eq("article-1"),
                org.mockito.ArgumentMatchers.eq("claim-1"),
                org.mockito.ArgumentMatchers.eq("RATE_LIMIT"), any(),
                org.mockito.ArgumentMatchers.eq(NOW));
    }

    @Test
    void timeoutIsDeferredWithoutEscaping() {
        arrangeClaim();
        when(provider.enrich(any())).thenThrow(new AiProviderException(
                AiProviderException.Kind.TIMEOUT_NETWORK, "timeout"));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));

        assertThat(service.attempt("article-1"))
                .isEqualTo(ArticleEnrichmentService.Outcome.DEFERRED);
        verify(jobs).markDeferred(
                org.mockito.ArgumentMatchers.eq("article-1"),
                org.mockito.ArgumentMatchers.eq("claim-1"),
                org.mockito.ArgumentMatchers.eq("TIMEOUT_NETWORK"), any(),
                org.mockito.ArgumentMatchers.eq(NOW));
    }

    @Test
    void respectsRetryAfterWhenDeferred() {
        arrangeClaim();
        when(provider.enrich(any())).thenThrow(new AiProviderException(
                "GEMINI",
                AiProviderException.Kind.RATE_LIMIT,
                "limited",
                429,
                "RESOURCE_EXHAUSTED",
                "Rate limit exceeded",
                "gemini-test",
                Duration.ofSeconds(45),
                null));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));

        assertThat(service.attempt("article-1"))
                .isEqualTo(ArticleEnrichmentService.Outcome.DEFERRED);
        verify(jobs).markDeferred(
                "article-1", "claim-1", "RATE_LIMIT",
                NOW.plus(Duration.ofSeconds(45)), NOW);
    }

    @Test
    void permanentFailureIsRecordedAndDoesNotEscape() {
        arrangeClaim();
        when(provider.enrich(any())).thenThrow(new AiProviderException(
                AiProviderException.Kind.INVALID_REQUEST, "invalid"));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));

        assertThat(service.attempt("article-1"))
                .isEqualTo(ArticleEnrichmentService.Outcome.FAILED);
        verify(jobs).markFailed("article-1", "claim-1", "INVALID_REQUEST", NOW);
    }

    @Test
    void missingClaimPreventsConcurrentProviderCall() {
        when(articleService.findById("article-1")).thenReturn(Optional.of(article()));
        when(jobs.claim(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Optional.empty());

        assertThat(service.attempt("article-1"))
                .isEqualTo(ArticleEnrichmentService.Outcome.NOT_DUE_OR_CLAIMED);
        verify(provider, never()).enrich(any());
    }

    @Test
    void finalRetryableAttemptBecomesFailedInsteadOfRemainingDeferredForever() {
        when(articleService.findById("article-1")).thenReturn(Optional.of(article()));
        when(jobs.claim("article-1", NOW, Duration.ofMinutes(2), 5))
                .thenReturn(Optional.of(new ArticleEnrichmentJob(
                        "article-1", EnrichmentStatus.PROCESSING, 5, null,
                        NOW.plus(Duration.ofMinutes(2)), "claim-5", null, NOW)));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article()));
        when(provider.enrich(any())).thenThrow(new AiProviderException(
                AiProviderException.Kind.TIMEOUT_NETWORK, "timeout"));

        assertThat(service.attempt("article-1"))
                .isEqualTo(ArticleEnrichmentService.Outcome.FAILED);
        verify(jobs).markFailed(
                "article-1", "claim-5", "RETRIES_EXHAUSTED_TIMEOUT_NETWORK", NOW);
    }

    private void arrangeClaim() {
        when(articleService.findById("article-1")).thenReturn(Optional.of(article()));
        when(jobs.claim("article-1", NOW, Duration.ofMinutes(2), 5))
                .thenReturn(Optional.of(new ArticleEnrichmentJob(
                        "article-1", EnrichmentStatus.PROCESSING, 1, null,
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

    private AiResult result() {
        return new AiResult(
                "A useful summary.", ArticleCategory.LOCAL,
                List.of("topic"), List.of("keyword"), List.of());
    }

    private EnrichmentRetryProperties retryProperties() {
        return new EnrichmentRetryProperties(
                true, 5, 5, Duration.ofMinutes(5), Duration.ofHours(6),
                Duration.ofMinutes(2));
    }
}
