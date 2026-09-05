package lk.srilankannews.story.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lk.srilankannews.ai.EmbeddingProvider;
import lk.srilankannews.ai.SemanticSimilarityEmbeddingInput;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleSemanticEmbedding;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.api.ArticleLocalizationService;
import lk.srilankannews.article.api.LocalizedContentResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryEmbeddingProperties;
import lk.srilankannews.story.StoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class AskStoryServiceTest {
    private static final String STORY_ID = "507f1f77bcf86cd799439011";
    private static final Instant NOW = Instant.parse("2026-08-30T08:00:00Z");

    @Mock StoryRepository storyRepository;
    @Mock ArticleRepository articleRepository;
    @Mock SourceService sourceService;
    @Mock EmbeddingProvider embeddingProvider;
    @Mock GroundedAnswerProvider answerProvider;
    @Mock ArticleLocalizationService localizationService;
    @Mock lk.srilankannews.analytics.AnalyticsRecorder analyticsRecorder;

    AskStoryService service;

    @BeforeEach
    void setUp() {
        AskStoryProperties ask = properties(5, 6000, 24000);
        service = new AskStoryService(
                storyRepository, articleRepository, sourceService, embeddingProvider,
                answerProvider, localizationService, new AskStoryQuestionNormalizer(ask),
                new StoryGroundingContextBuilder(ask), embeddingProperties(), ask, analyticsRecorder);
        when(storyRepository.findById(STORY_ID)).thenReturn(java.util.Optional.of(story()));
    }

    @Test
    void normalizesQuestionEmbedsOnceRanksOnlyStoryMembersAndMapsTrustedCitations() {
        Article first = article("a1", "source-1", List.of(1.0, 0.0, 0.0), "https://trusted/one");
        Article second = article("a2", "source-2", List.of(0.0, 1.0, 0.0), "https://trusted/two");
        stubMembers(first, second);
        when(embeddingProvider.embed(SemanticSimilarityEmbeddingInput.format("Café update")))
                .thenReturn(List.of(1.0, 0.0, 0.0));
        when(answerProvider.answer(any())).thenReturn(
                new GroundedAnswerResult(true, "The first report explains it [S1].", List.of("S1", "S1")));
        when(localizationService.localize(first, Language.SI)).thenReturn(
                new LocalizedContentResponse(Language.SI, Language.SI, true, false,
                        "දේශීය මාතෘකාව", "සාරාංශය"));

        AskStoryResponse response = service.ask(
                STORY_ID, new AskStoryRequest("  Cafe\u0301   update  ", Language.SI));

        assertThat(response.answer()).isEqualTo("The first report explains it [1].");
        assertThat(response.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.articleId()).isEqualTo("a1");
            assertThat(citation.title()).isEqualTo("දේශීය මාතෘකාව");
            assertThat(citation.originalUrl()).isEqualTo("https://trusted/one");
        });
        verify(embeddingProvider, times(1)).embed(any());
        verify(answerProvider, times(1)).answer(any());
        verify(articleRepository).findByStoryId(org.mockito.ArgumentMatchers.eq(STORY_ID), any(Sort.class));
        verify(articleRepository, never()).save(any());
        ArgumentCaptor<GroundedAnswerInput> input = ArgumentCaptor.forClass(GroundedAnswerInput.class);
        verify(answerProvider).answer(input.capture());
        assertThat(input.getValue().sources()).extracting(GroundedSourceContext::id)
                .containsExactly("S1", "S2");
        assertThat(input.getValue().sources()).extracting(GroundedSourceContext::title)
                .doesNotContain("other-story");
    }

    @Test
    void missingOrIncompatibleEmbeddingsUseDeterministicFallbackAndSingleArticleWorks() {
        Article article = article("a1", "source-1", null, "https://trusted/one");
        stubMembers(article);
        when(embeddingProvider.embed(any())).thenReturn(List.of(1.0, 0.0, 0.0));
        when(answerProvider.answer(any())).thenReturn(
                new GroundedAnswerResult(true, "Available evidence [S1].", List.of("S1")));
        when(localizationService.localize(article, Language.EN)).thenReturn(
                new LocalizedContentResponse(Language.EN, Language.EN, false, false,
                        article.title(), null));

        AskStoryResponse response = service.ask(
                STORY_ID, new AskStoryRequest("What happened?", Language.EN));

        assertThat(response.answerable()).isTrue();
        assertThat(response.citations()).hasSize(1);
    }

    @Test
    void insufficientEvidenceIsSuccessfulAndSkipsBothProviders() {
        Article unusable = new Article(
                "a1", "source-1", " ", "https://trusted/one", "https://trusted/one",
                Language.EN, List.of(), NOW, NOW, ArticleCategory.LOCAL, " ", null,
                null, null, Map.of(), ProcessingStatus.COMPLETED, STORY_ID, NOW, NOW);
        when(articleRepository.findByStoryId(org.mockito.ArgumentMatchers.eq(STORY_ID), any(Sort.class)))
                .thenReturn(List.of(unusable));

        AskStoryResponse response = service.ask(
                STORY_ID, new AskStoryRequest("What happened?", Language.TA));

        assertThat(response.answerable()).isFalse();
        assertThat(response.citations()).isEmpty();
        verify(embeddingProvider, never()).embed(any());
        verify(answerProvider, never()).answer(any());
    }

    @Test
    void invalidOrInventedCitationsAndProviderFailuresBecomeSanitizedUnavailableOutcome() {
        Article article = article("a1", "source-1", List.of(1.0, 0.0, 0.0), "https://trusted/one");
        stubMembers(article);
        when(embeddingProvider.embed(any())).thenReturn(List.of(1.0, 0.0, 0.0));
        when(answerProvider.answer(any())).thenReturn(
                new GroundedAnswerResult(true, "Invented claim [S99].", List.of("S99")));

        assertThatThrownBy(() -> service.ask(
                STORY_ID, new AskStoryRequest("What happened?", Language.EN)))
                .isInstanceOf(AskStoryUnavailableException.class)
                .hasMessageNotContaining("Invented claim");
    }

    @Test
    void displayLanguageDoesNotChangeRetrievedArticleIds() {
        Article first = article("a1", "source-1", List.of(1.0, 0.0, 0.0), "https://trusted/one");
        Article second = article("a2", "source-2", List.of(0.0, 1.0, 0.0), "https://trusted/two");
        stubMembers(first, second);
        when(embeddingProvider.embed(any())).thenReturn(List.of(1.0, 0.0, 0.0));
        when(answerProvider.answer(any())).thenReturn(
                new GroundedAnswerResult(false, "Not enough evidence.", List.of()));

        service.ask(STORY_ID, new AskStoryRequest("What changed?", Language.EN));
        service.ask(STORY_ID, new AskStoryRequest("What changed?", Language.TA));

        ArgumentCaptor<GroundedAnswerInput> inputs = ArgumentCaptor.forClass(GroundedAnswerInput.class);
        verify(answerProvider, times(2)).answer(inputs.capture());
        assertThat(inputs.getAllValues().get(0).sources().stream().map(GroundedSourceContext::title).toList())
                .isEqualTo(inputs.getAllValues().get(1).sources().stream().map(GroundedSourceContext::title).toList());
    }

    @Test
    void incompatibleEmbeddingCannotOutrankCompatibleEmbedding() {
        Article compatible = article("a1", "source-1", List.of(0.8, 0.2, 0.0), "https://trusted/one");
        Article incompatible = articleWithEmbedding("a2", "source-2",
                new ArticleSemanticEmbedding(List.of(1.0, 0.0, 0.0),
                        "old-model", 3, "old-version", "hash", NOW));
        stubMembers(compatible, incompatible);
        when(embeddingProvider.embed(any())).thenReturn(List.of(1.0, 0.0, 0.0));
        when(answerProvider.answer(any())).thenReturn(
                new GroundedAnswerResult(true, "Compatible report [S1].", List.of("S1")));
        when(localizationService.localize(compatible, Language.EN)).thenReturn(
                new LocalizedContentResponse(Language.EN, Language.EN, false, false,
                        compatible.title(), null));

        AskStoryResponse response = service.ask(
                STORY_ID, new AskStoryRequest("What happened?", Language.EN));

        assertThat(response.citations()).extracting(AskStoryCitationResponse::articleId)
                .containsExactly("a1");
    }

    @Test
    void retrievalCountIsBoundedAndPreservesSourceDiversityDeterministically() {
        AskStoryProperties ask = properties(2, 6000, 24000);
        service = new AskStoryService(
                storyRepository, articleRepository, sourceService, embeddingProvider,
                answerProvider, localizationService, new AskStoryQuestionNormalizer(ask),
                new StoryGroundingContextBuilder(ask), embeddingProperties(), ask, analyticsRecorder);
        List<Article> articles = List.of(
                article("a1", "source-1", List.of(1.0, 0.0, 0.0), "https://trusted/1"),
                article("a2", "source-1", List.of(0.99, 0.01, 0.0), "https://trusted/2"),
                article("a3", "source-2", List.of(0.98, 0.02, 0.0), "https://trusted/3"));
        stubMembers(articles.toArray(Article[]::new));
        when(embeddingProvider.embed(any())).thenReturn(List.of(1.0, 0.0, 0.0));
        when(answerProvider.answer(any())).thenReturn(
                new GroundedAnswerResult(false, "Not enough evidence.", List.of()));

        service.ask(STORY_ID, new AskStoryRequest("Compare reports", Language.EN));

        ArgumentCaptor<GroundedAnswerInput> input = ArgumentCaptor.forClass(GroundedAnswerInput.class);
        verify(answerProvider).answer(input.capture());
        assertThat(input.getValue().sources()).hasSize(2);
        assertThat(input.getValue().sources()).extracting(GroundedSourceContext::publisher)
                .containsExactly("Publisher source-1", "Publisher source-2");
    }

    private void stubMembers(Article... articles) {
        when(articleRepository.findByStoryId(org.mockito.ArgumentMatchers.eq(STORY_ID), any(Sort.class)))
                .thenReturn(List.of(articles));
        Set<String> sourceIds = java.util.Arrays.stream(articles).map(Article::sourceId).collect(java.util.stream.Collectors.toSet());
        when(sourceService.findAllByIds(sourceIds)).thenReturn(sourceIds.stream().map(this::source).toList());
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
        ArticleSemanticEmbedding embedding = vector == null ? null : new ArticleSemanticEmbedding(
                vector, "gemini-embedding-2", 3, "story-semantic-v2", "hash", NOW);
        return new Article(id, sourceId, "Title " + id, originalUrl, originalUrl,
                Language.EN, List.of(), NOW.plusSeconds(id.equals("a1") ? 0 : 60), NOW,
                ArticleCategory.LOCAL, "Private extracted evidence " + id, "hash-" + id,
                null, embedding, Map.of(), ProcessingStatus.COMPLETED, STORY_ID, NOW, NOW);
    }

    private Article articleWithEmbedding(
            String id, String sourceId, ArticleSemanticEmbedding embedding) {
        return new Article(id, sourceId, "Title " + id, "https://trusted/" + id,
                "https://trusted/" + id, Language.EN, List.of(), NOW, NOW,
                ArticleCategory.LOCAL, "Private extracted evidence " + id, "hash-" + id,
                null, embedding, Map.of(), ProcessingStatus.COMPLETED, STORY_ID, NOW, NOW);
    }

    private StoryEmbeddingProperties embeddingProperties() {
        return new StoryEmbeddingProperties(
                "gemini-embedding-2", 3, "story-semantic-v2", 8000, 0.82, 0.90, 0);
    }

    private AskStoryProperties properties(int retrieved, int articleChars, int totalChars) {
        return new AskStoryProperties("ask-story-v1", 500, retrieved, articleChars, totalChars, 3000);
    }
}
