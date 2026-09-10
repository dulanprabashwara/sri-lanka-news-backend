package lk.srilankannews.story.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.openrouter.OpenRouterProperties;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.processing.enrichment.GeminiBackgroundProperties;
import lk.srilankannews.processing.enrichment.GeminiRequestController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResilientGroundedAnswerProviderTest {
    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");

    private GroundedAnswerProvider primary;
    private GroundedAnswerProvider fallback;
    private GeminiRequestController requestController;
    private ResilientGroundedAnswerProvider resilientProvider;

    @BeforeEach
    void setUp() {
        primary = mock(GroundedAnswerProvider.class);
        fallback = mock(GroundedAnswerProvider.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        requestController = new GeminiRequestController(
                new GeminiBackgroundProperties(1, Duration.ZERO, Duration.ofMinutes(1)), clock);
        OpenRouterProperties openRouterProperties = new OpenRouterProperties(
                "key-123", "openrouter/free", "https://openrouter.ai/api/v1", Duration.ofSeconds(10));
        resilientProvider = new ResilientGroundedAnswerProvider(
                primary, fallback, openRouterProperties, requestController);
    }

    @Test
    void primarySuccessDoesNotCallFallback() {
        GroundedAnswerResult expected = new GroundedAnswerResult(true, "Primary answer [S1]", List.of("S1"));
        when(primary.answer(any())).thenReturn(expected);

        GroundedAnswerResult result = resilientProvider.answer(sampleInput());

        assertThat(result).isEqualTo(expected);
        verify(fallback, never()).answer(any());
    }

    @Test
    void primary429FallsBackToOpenRouterSuccess() {
        when(primary.answer(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota exceeded",
                "gemini-2.5", Duration.ofSeconds(60), null));

        GroundedAnswerResult expected = new GroundedAnswerResult(true, "Fallback answer [S1]", List.of("S1"));
        when(fallback.answer(any())).thenReturn(expected);

        GroundedAnswerResult result = resilientProvider.answer(sampleInput());

        assertThat(result).isEqualTo(expected);
        verify(fallback).answer(any());
        assertThat(requestController.isCoolingDown()).isTrue();
    }

    @Test
    void primaryTimeoutFallsBackToOpenRouterSuccess() {
        when(primary.answer(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.TIMEOUT_NETWORK, "Timeout", null, "TIMEOUT", "Timeout",
                "gemini-2.5", null, null));

        GroundedAnswerResult expected = new GroundedAnswerResult(true, "Fallback answer [S1]", List.of("S1"));
        when(fallback.answer(any())).thenReturn(expected);

        GroundedAnswerResult result = resilientProvider.answer(sampleInput());

        assertThat(result).isEqualTo(expected);
        verify(fallback).answer(any());
    }

    @Test
    void primary5xxFallsBackToOpenRouterSuccess() {
        when(primary.answer(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.PROVIDER_5XX, "503", 503, "SERVER_ERROR", "Server error",
                "gemini-2.5", null, null));

        GroundedAnswerResult expected = new GroundedAnswerResult(true, "Fallback answer [S1]", List.of("S1"));
        when(fallback.answer(any())).thenReturn(expected);

        GroundedAnswerResult result = resilientProvider.answer(sampleInput());

        assertThat(result).isEqualTo(expected);
        verify(fallback).answer(any());
    }

    @Test
    void primaryNonRetryableFailsFastWithoutFallback() {
        when(primary.answer(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.INVALID_REQUEST, "Bad Request", 400, "BAD_REQUEST", "Bad Request",
                "gemini-2.5", null, null));

        assertThatThrownBy(() -> resilientProvider.answer(sampleInput()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> assertThat(((AiProviderException) ex).kind())
                        .isEqualTo(AiProviderException.Kind.INVALID_REQUEST));

        verify(fallback, never()).answer(any());
    }

    @Test
    void bothFailThrowsException() {
        when(primary.answer(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota",
                "gemini-2.5", null, null));
        when(fallback.answer(any())).thenThrow(new AiProviderException(
                "OPENROUTER", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota",
                "openrouter/free", null, null));

        assertThatThrownBy(() -> resilientProvider.answer(sampleInput()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> assertThat(((AiProviderException) ex).provider())
                        .isEqualTo("OPENROUTER"));
    }

    @Test
    void cooldownActiveSkipsPrimaryAndCallsFallbackDirectly() {
        requestController.recordRateLimit(Duration.ofMinutes(5));
        assertThat(requestController.isCoolingDown()).isTrue();

        GroundedAnswerResult expected = new GroundedAnswerResult(true, "Fallback answer [S1]", List.of("S1"));
        when(fallback.answer(any())).thenReturn(expected);

        GroundedAnswerResult result = resilientProvider.answer(sampleInput());

        assertThat(result).isEqualTo(expected);
        verify(primary, never()).answer(any());
        verify(fallback).answer(any());
    }

    @Test
    void fallbackNotConfiguredThrowsPrimaryException() {
        OpenRouterProperties unconfigured = new OpenRouterProperties(
                "", "openrouter/free", "https://openrouter.ai/api/v1", Duration.ofSeconds(10));
        ResilientGroundedAnswerProvider noFallbackProvider = new ResilientGroundedAnswerProvider(
                primary, fallback, unconfigured, requestController);

        assertThat(noFallbackProvider.hasFallback()).isFalse();

        when(primary.answer(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota",
                "gemini-2.5", null, null));

        assertThatThrownBy(() -> noFallbackProvider.answer(sampleInput()))
                .isInstanceOf(AiProviderException.class);
        verify(fallback, never()).answer(any());
    }

    private GroundedAnswerInput sampleInput() {
        return new GroundedAnswerInput(
                "Question?",
                Language.EN,
                "Title",
                ArticleCategory.LOCAL,
                Instant.parse("2026-09-10T10:00:00Z"),
                Instant.parse("2026-09-10T12:00:00Z"),
                "Inventory",
                List.of());
    }
}
