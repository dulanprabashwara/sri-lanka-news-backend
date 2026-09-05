package lk.srilankannews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.source.api.SourceApiMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForYouServiceTest {
    @Mock UserPreferencesService preferencesService;
    @Mock UserFollowRepository followRepository;
    @Mock ArticleService articleService;
    @Mock SourceService sourceService;
    @Mock ArticleApiMapper articleMapper;

    private ForYouService service;
    private final Source sourceOne = source("source-1", "NewsFirst", "newsfirst");
    private final Source sourceTwo = source("source-2", "Hiru News", "hiru-news-sinhala");

    @BeforeEach
    void setUp() {
        service = new ForYouService(preferencesService, followRepository, articleService,
                sourceService, new ForYouRanking(new TopicNormalizer()), articleMapper,
                new ForYouProperties(500));
        when(preferencesService.get(any())).thenReturn(preferences(
                DisplayLanguagePreference.ORIGINAL, List.of()));
        when(followRepository.findAllByUserId(any())).thenReturn(List.of());
        when(sourceService.findAllByIds(any())).thenReturn(List.of(sourceOne, sourceTwo));
        org.mockito.Mockito.lenient().when(
                articleMapper.toResponse(any(), any(), nullable(Language.class)))
                .thenAnswer(invocation -> response(
                        invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test
    void coldStartReturnsDeterministicLatestFallbackFromBoundedPool() {
        Article newer = article("b", sourceOne.id(), "Newer", ArticleCategory.LOCAL,
                List.of(), "2026-09-02T10:00:00Z");
        Article older = article("a", sourceTwo.id(), "Older", ArticleCategory.SPORTS,
                List.of(), "2026-09-02T09:00:00Z");
        when(articleService.findRecent(500)).thenReturn(List.of(newer, older));

        ForYouFeedResponse result = service.feed("user-a", 0, 20, null);

        assertThat(result.content()).extracting(item -> item.article().id())
                .containsExactly("b", "a");
        assertThat(result.content()).allMatch(item -> !item.personalized()
                && item.reasons().isEmpty());
        assertThat(result.personalization()).isEqualTo(new PersonalizationResponse(false, 0));
        verify(articleService).findRecent(500);
        verify(followRepository).findAllByUserId("user-a");
        verify(sourceService).findAllByIds(any());
    }

    @Test
    void combinesSignalsCapsTopicsAndRanksFallbackLast() {
        when(preferencesService.get("user-a")).thenReturn(preferences(
                DisplayLanguagePreference.ORIGINAL, List.of(ArticleCategory.POLITICS)));
        when(followRepository.findAllByUserId("user-a")).thenReturn(List.of(
                follow(FollowTargetType.SOURCE, sourceOne.id(), sourceOne.name()),
                follow(FollowTargetType.TOPIC, "elections", "Elections"),
                follow(FollowTargetType.TOPIC, "parliament", "Parliament"),
                follow(FollowTargetType.TOPIC, "budget", "Budget")));
        Article allSignals = article("a", sourceOne.id(), "All", ArticleCategory.POLITICS,
                List.of(" Elections ", "Parliament", "Budget"), "2026-09-02T08:00:00Z");
        Article topicOnly = article("b", sourceTwo.id(), "Topic", ArticleCategory.LOCAL,
                List.of("ELECTIONS"), "2026-09-02T10:00:00Z");
        Article fallback = article("c", sourceTwo.id(), "Fallback", ArticleCategory.LOCAL,
                List.of("Weather"), "2026-09-02T11:00:00Z");
        when(articleService.findRecent(500)).thenReturn(List.of(fallback, topicOnly, allSignals));

        ForYouFeedResponse result = service.feed("user-a", 0, 20, null);

        assertThat(result.content()).extracting(item -> item.article().id())
                .containsExactly("a", "b", "c");
        assertThat(result.content().get(0).reasons()).hasSize(4)
                .extracting(RecommendationReasonResponse::type)
                .containsExactly(RecommendationReasonType.FOLLOWED_SOURCE,
                        RecommendationReasonType.FOLLOWED_TOPIC,
                        RecommendationReasonType.FOLLOWED_TOPIC,
                        RecommendationReasonType.PREFERRED_CATEGORY);
        assertThat(result.content().get(0).reasons())
                .extracting(RecommendationReasonResponse::label)
                .contains("NewsFirst", "Elections", "Parliament", "POLITICS")
                .doesNotContain("Budget");
        assertThat(result.content().get(2).personalized()).isFalse();
        assertThat(result.personalization().signalCount()).isEqualTo(5);
    }

    @Test
    void equalScoresUsePublicationThenDescendingIdAsStableTies() {
        when(preferencesService.get("user-a")).thenReturn(preferences(
                DisplayLanguagePreference.ORIGINAL, List.of(ArticleCategory.LOCAL)));
        Article oldest = article("z", sourceOne.id(), "Old", ArticleCategory.LOCAL,
                List.of(), "2026-09-02T08:00:00Z");
        Article idA = article("a", sourceOne.id(), "A", ArticleCategory.LOCAL,
                List.of(), "2026-09-02T10:00:00Z");
        Article idB = article("b", sourceOne.id(), "B", ArticleCategory.LOCAL,
                List.of(), "2026-09-02T10:00:00Z");
        when(articleService.findRecent(500)).thenReturn(List.of(oldest, idA, idB));

        assertThat(service.feed("user-a", 0, 20, null).content())
                .extracting(item -> item.article().id()).containsExactly("b", "a", "z");
    }

    @Test
    void exactTopicAndSourceIdentityAvoidFalseCrossLanguageOrStaleMatches() {
        when(followRepository.findAllByUserId("user-a")).thenReturn(List.of(
                follow(FollowTargetType.SOURCE, "deleted-source", "Deleted"),
                follow(FollowTargetType.TOPIC, "elections", "Elections"),
                follow(FollowTargetType.TOPIC, "මැතිවරණ", "මැතිවරණ")));
        Article article = article("a", sourceOne.id(), "Article", ArticleCategory.LOCAL,
                List.of(" ELECTIONS ", "Election"), "2026-09-02T10:00:00Z");
        when(articleService.findRecent(500)).thenReturn(List.of(article));

        ForYouFeedResponse result = service.feed("user-a", 0, 20, null);

        assertThat(result.content().get(0).reasons()).singleElement()
                .extracting(RecommendationReasonResponse::type)
                .isEqualTo(RecommendationReasonType.FOLLOWED_TOPIC);
        assertThat(result.personalization().signalCount()).isEqualTo(2);
    }

    @Test
    void paginatesRankedPoolAndExplicitLanguageOverridesSavedPreference() {
        when(preferencesService.get("user-a")).thenReturn(preferences(
                DisplayLanguagePreference.SI, List.of()));
        when(articleService.findRecent(500)).thenReturn(List.of(
                article("c", sourceOne.id(), "C", ArticleCategory.LOCAL, List.of(), "2026-09-02T12:00:00Z"),
                article("b", sourceOne.id(), "B", ArticleCategory.LOCAL, List.of(), "2026-09-02T11:00:00Z"),
                article("a", sourceOne.id(), "A", ArticleCategory.LOCAL, List.of(), "2026-09-02T10:00:00Z")));

        ForYouFeedResponse saved = service.feed("user-a", 1, 2, null);
        ForYouFeedResponse explicit = service.feed("user-a", 0, 2, Language.TA);

        assertThat(saved.content()).extracting(item -> item.article().id()).containsExactly("a");
        assertThat(saved.totalElements()).isEqualTo(3);
        assertThat(saved.totalPages()).isEqualTo(2);
        assertThat(saved.first()).isFalse();
        assertThat(saved.last()).isTrue();
        ArgumentCaptor<Language> language = ArgumentCaptor.forClass(Language.class);
        verify(articleMapper, times(3)).toResponse(any(), any(), language.capture());
        assertThat(language.getAllValues()).containsExactly(Language.SI, Language.TA, Language.TA);
    }

    @Test
    void userSignalsAreLoadedOnlyForAuthenticatedOwnerAndNoExternalProvidersAreUsed() {
        when(articleService.findRecent(anyInt())).thenReturn(List.of());

        service.feed("user-b", 0, 20, null);

        verify(preferencesService).get("user-b");
        verify(followRepository).findAllByUserId("user-b");
        verify(articleService).findRecent(500);
        verify(sourceService).findAllByIds(Set.of());
        verify(articleMapper, never()).toResponse(any(), any(), any());
    }

    private UserPreferencesResponse preferences(
            DisplayLanguagePreference language, List<ArticleCategory> categories) {
        return new UserPreferencesResponse(language, categories, true, null, null);
    }

    private UserFollow follow(FollowTargetType type, String key, String label) {
        return new UserFollow("follow-" + key, "user-a", type, key, label,
                Instant.parse("2026-09-02T00:00:00Z"));
    }

    private Article article(String id, String sourceId, String title, ArticleCategory category,
            List<String> topics, String publishedAt) {
        Instant published = Instant.parse(publishedAt);
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                "Summary", topics, List.of(), List.of(), "model", "v1", published);
        return new Article(id, sourceId, title, "https://example.com/" + id,
                "https://example.com/" + id, Language.EN, List.of(), published, published,
                category, "Internal content", "hash-" + id, enrichment, null, null,
                null, published, published);
    }

    private ArticleResponse response(Article article, Source source) {
        return new ArticleResponse(article.id(), article.title(), article.originalUrl(),
                article.originalLanguage(), article.authors(), article.publishedAt(),
                article.discoveredAt(), article.category(), article.aiEnrichment().summary(),
                article.aiEnrichment().topics(), new SourceApiMapper().toSummary(source));
    }

    private static Source source(String id, String name, String slug) {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        return new Source(id, name, slug, "https://example.com/" + slug, Language.EN,
                IngestionType.RSS, true, now, now);
    }
}
