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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.EmbeddingProvider;
import lk.srilankannews.ai.openrouter.OpenRouterProperties;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.ArticleSemanticEmbedding;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.article.api.ArticleLocalizationService;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.processing.enrichment.GeminiBackgroundProperties;
import lk.srilankannews.processing.enrichment.GeminiRequestController;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryEmbeddingProperties;
import lk.srilankannews.story.StoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class AskStoryServiceOpenRouterTest {
    private static final String STORY_ID = "507f1f77bcf86cd799439011";
    private static final Instant NOW = Instant.parse("2026-08-30T08:00:00Z");

    private StoryRepository storyRepository;
    private ArticleRepository articleRepository;
    private SourceService sourceService;
    private EmbeddingProvider embeddingProvider;
    private GroundedAnswerProvider geminiAnswerProvider;
    private GroundedAnswerProvider openRouterAnswerProvider;
    private ArticleLocalizationService localizationService;
    private lk.srilankannews.analytics.AnalyticsRecorder analyticsRecorder;
    private GeminiRequestController geminiRequestController;
    private ResilientGroundedAnswerProvider resilientAnswerProvider;
    private AskStoryService service;

    @BeforeEach
    void setUp() {
        storyRepository = mock(StoryRepository.class);
        articleRepository = mock(ArticleRepository.class);
        sourceService = mock(SourceService.class);
        embeddingProvider = mock(EmbeddingProvider.class);
        geminiAnswerProvider = mock(GroundedAnswerProvider.class);
        openRouterAnswerProvider = mock(GroundedAnswerProvider.class);
        localizationService = mock(ArticleLocalizationService.class);
        analyticsRecorder = mock(lk.srilankannews.analytics.AnalyticsRecorder.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        geminiRequestController = new GeminiRequestController(
                new GeminiBackgroundProperties(1, Duration.ZERO, Duration.ofMinutes(1)), clock);
        OpenRouterProperties openRouterProperties = new OpenRouterProperties(
                "key-123", "openrouter/free", "https://openrouter.ai/api/v1", Duration.ofSeconds(10));
        resilientAnswerProvider = new ResilientGroundedAnswerProvider(
                geminiAnswerProvider, openRouterAnswerProvider, openRouterProperties, geminiRequestController);

        AskStoryProperties ask = new AskStoryProperties("ask-story-v1", 500, 5, 6000, 24000, 3000);
        StoryEmbeddingProperties embeddingProps = new StoryEmbeddingProperties(
                "gemini-embedding-2", 3, "story-semantic-v2", 8000, 0.82, 0.90, 0);

        service = new AskStoryService(
                storyRepository, articleRepository, sourceService, embeddingProvider,
                resilientAnswerProvider, localizationService, new AskStoryQuestionNormalizer(ask),
                new StoryGroundingContextBuilder(ask), embeddingProps, ask,
                analyticsRecorder, geminiRequestController);

        when(storyRepository.findById(STORY_ID)).thenReturn(Optional.of(story()));
        when(sourceService.findAllByIds(any())).thenReturn(List.of(source("source-1"), source("source-2")));
        Article first = article("a1", "source-1", List.of(1.0, 0.0, 0.0), "https://trusted/one");
        Article second = article("a2", "source-2", List.of(0.0, 1.0, 0.0), "https://trusted/two");
        when(articleRepository.findByStoryId(org.mockito.ArgumentMatchers.eq(STORY_ID), any(Sort.class)))
                .thenReturn(List.of(first, second));
        when(embeddingProvider.embed(any())).thenReturn(List.of(1.0, 0.0, 0.0));
    }

    @Test
    void geminiSuccessDoesNotCallOpenRouter() {
        when(geminiAnswerProvider.answer(any())).thenReturn(
                new GroundedAnswerResult(true, "President visited Colombo port [S1].", List.of("S1")));

        AskStoryResponse response = service.ask(STORY_ID, new AskStoryRequest("What happened?", Language.EN));

        assertThat(response.answerable()).isTrue();
        assertThat(response.answer()).isEqualTo("President visited Colombo port [1].");
        assertThat(response.citations()).hasSize(1);
        verify(openRouterAnswerProvider, never()).answer(any());
    }

    @Test
    void gemini429FallsBackToOpenRouterSuccess() {
        when(geminiAnswerProvider.answer(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota exceeded",
                "gemini-2.5", Duration.ofSeconds(60), null));
        when(openRouterAnswerProvider.answer(any())).thenReturn(
                new GroundedAnswerResult(true, "President visited Colombo port [S1].", List.of("S1")));

        AskStoryResponse response = service.ask(STORY_ID, new AskStoryRequest("What happened?", Language.EN));

        assertThat(response.answerable()).isTrue();
        assertThat(response.answer()).isEqualTo("President visited Colombo port [1].");
        verify(openRouterAnswerProvider).answer(any());
        assertThat(geminiRequestController.isCoolingDown()).isTrue();
    }

    @Test
    void geminiTimeoutFallsBackToOpenRouterSuccess() {
        when(geminiAnswerProvider.answer(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.TIMEOUT_NETWORK, "Timeout", null, "TIMEOUT", "Timeout",
                "gemini-2.5", null, null));
        when(openRouterAnswerProvider.answer(any())).thenReturn(
                new GroundedAnswerResult(true, "President visited Colombo port [S1].", List.of("S1")));

        AskStoryResponse response = service.ask(STORY_ID, new AskStoryRequest("What happened?", Language.EN));

        assertThat(response.answerable()).isTrue();
        assertThat(response.answer()).isEqualTo("President visited Colombo port [1].");
        verify(openRouterAnswerProvider).answer(any());
    }

    @Test
    void bothFailResultsInGraceful503() {
        when(geminiAnswerProvider.answer(any())).thenThrow(new AiProviderException(
                "GEMINI", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota exceeded",
                "gemini-2.5", null, null));
        when(openRouterAnswerProvider.answer(any())).thenThrow(new AiProviderException(
                "OPENROUTER", AiProviderException.Kind.RATE_LIMIT, "429", 429, "QUOTA", "Quota exceeded",
                "openrouter/free", null, null));

        assertThatThrownBy(() -> service.ask(STORY_ID, new AskStoryRequest("What happened?", Language.EN)))
                .isInstanceOf(AskStoryUnavailableException.class);
    }

    @Test
    void geminiCooldownWithOpenRouterFallbackProceedsAndAnswers() {
        geminiRequestController.recordRateLimit(Duration.ofMinutes(5));
        assertThat(geminiRequestController.isCoolingDown()).isTrue();

        when(openRouterAnswerProvider.answer(any())).thenReturn(
                new GroundedAnswerResult(true, "President visited Colombo port [S1].", List.of("S1")));

        AskStoryResponse response = service.ask(STORY_ID, new AskStoryRequest("What happened?", Language.EN));

        assertThat(response.answerable()).isTrue();
        assertThat(response.answer()).isEqualTo("President visited Colombo port [1].");
        verify(geminiAnswerProvider, never()).answer(any());
        verify(openRouterAnswerProvider).answer(any());
    }

    @Test
    void geminiCooldownWithoutFallbackFailsFastBeforeEmbedding() {
        GroundedAnswerProvider noFallback = mock(GroundedAnswerProvider.class);
        when(noFallback.hasFallback()).thenReturn(false);

        AskStoryProperties ask = new AskStoryProperties("ask-story-v1", 500, 5, 6000, 24000, 3000);
        StoryEmbeddingProperties embeddingProps = new StoryEmbeddingProperties(
                "gemini-embedding-2", 3, "story-semantic-v2", 8000, 0.82, 0.90, 0);

        AskStoryService unbackedService = new AskStoryService(
                storyRepository, articleRepository, sourceService, embeddingProvider,
                noFallback, localizationService, new AskStoryQuestionNormalizer(ask),
                new StoryGroundingContextBuilder(ask), embeddingProps, ask,
                analyticsRecorder, geminiRequestController);

        geminiRequestController.recordRateLimit(Duration.ofMinutes(5));

        assertThatThrownBy(() -> unbackedService.ask(STORY_ID, new AskStoryRequest("What happened?", Language.EN)))
                .isInstanceOf(AskStoryUnavailableException.class);

        verify(embeddingProvider, never()).embed(any());
        verify(noFallback, never()).answer(any());
    }

    private Story story() {
        return new Story(STORY_ID, "Story title", "a1", ArticleCategory.LOCAL,
                NOW, NOW.plusSeconds(3600), 2, Set.of("source-1", "source-2"),
                Set.of("a1", "a2"), NOW, NOW, "hybrid-v1");
    }

    private Source source(String id) {
        return new Source(id, "Publisher " + id, id, "https://example.com",
                Language.EN, IngestionType.RSS, true, NOW, NOW);
    }

    private Article article(String id, String sourceId, List<Double> vector, String originalUrl) {
        ArticleSemanticEmbedding embedding = new ArticleSemanticEmbedding(
                vector, "gemini-embedding-2", 3, "story-semantic-v2", "hash", NOW);
        return new Article(id, sourceId, "Title " + id, originalUrl, originalUrl,
                Language.EN, List.of(), NOW, NOW,
                ArticleCategory.LOCAL, "Private extracted evidence " + id, "hash-" + id,
                null, embedding, Map.of(), ProcessingStatus.COMPLETED, STORY_ID, NOW, NOW);
    }
}
