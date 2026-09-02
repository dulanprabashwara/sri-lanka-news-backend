package lk.srilankannews.story.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.api.ArticleLocalizationService;
import lk.srilankannews.article.api.LocalizedContentResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import lk.srilankannews.story.TrendingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrendingStoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Mock private StoryRepository storyRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private ArticleLocalizationService localizationService;

    private TrendingStoryService service;

    @BeforeEach
    void setUp() {
        service = new TrendingStoryService(
                storyRepository,
                articleRepository,
                localizationService,
                new TrendingProperties(72, 12, 3, 5, 500),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void usesExactWeightsFixedClockBoundedWindowAndRecencyDominates() {
        Story recent = story("000000000000000000000001", NOW.minusSeconds(60), 1,
                Set.of("source-1"));
        Story oldBroad = story("000000000000000000000002", NOW.minusSeconds(24 * 3600), 5,
                Set.of("source-1", "source-2", "source-3"));
        when(storyRepository.findTrendingCandidates(
                NOW.minusSeconds(72 * 3600), ArticleCategory.LOCAL, 500))
                .thenReturn(List.of(oldBroad, recent));

        List<TrendingStoryResponse> result = service.trending(10, ArticleCategory.LOCAL, null);

        assertThat(TrendingStoryService.RECENCY_WEIGHT).isEqualTo(0.60);
        assertThat(TrendingStoryService.SOURCE_WEIGHT).isEqualTo(0.25);
        assertThat(TrendingStoryService.REPORT_WEIGHT).isEqualTo(0.15);
        assertThat(result).extracting(TrendingStoryResponse::id)
                .containsExactly(recent.id(), oldBroad.id());
        verify(articleRepository, never()).findAllById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sourceDiversityAndReportCoverageIncreaseRankAndSourceIdsStayDistinct() {
        Instant published = NOW.minusSeconds(3600);
        Story diverse = story("000000000000000000000001", published, 2,
                Set.of("source-1", "source-2"));
        Story oneSource = story("000000000000000000000003", published, 2,
                Set.of("source-1"));
        Story oneReport = story("000000000000000000000002", published, 1,
                Set.of("source-1"));
        when(storyRepository.findTrendingCandidates(
                NOW.minusSeconds(72 * 3600), null, 500))
                .thenReturn(List.of(oneReport, oneSource, diverse));

        List<TrendingStoryResponse> result = service.trending(10, null, null);

        assertThat(result).extracting(TrendingStoryResponse::id)
                .containsExactly(diverse.id(), oneSource.id(), oneReport.id());
        assertThat(result.get(0).sourceCount()).isEqualTo(2);
    }

    @Test
    void appliesDeterministicCountAndIdTieBreakersAndLimit() {
        Instant published = NOW.minusSeconds(3600);
        Story largerCount = story("000000000000000000000001", published, 6,
                Set.of("a", "b", "c"));
        Story higherId = story("000000000000000000000003", published, 5,
                Set.of("a", "b", "c"));
        Story lowerId = story("000000000000000000000002", published, 5,
                Set.of("a", "b", "c"));
        when(storyRepository.findTrendingCandidates(
                NOW.minusSeconds(72 * 3600), null, 500))
                .thenReturn(List.of(lowerId, higherId, largerCount));

        List<TrendingStoryResponse> result = service.trending(2, null, null);

        assertThat(result).extracting(TrendingStoryResponse::id)
                .containsExactly(largerCount.id(), higherId.id());
    }

    @Test
    void returnsOnlyDeterministicReasonsAndNeverExposesRankingOrInternals() {
        Story story = story("000000000000000000000001", NOW.minusSeconds(3600), 2,
                Set.of("source-1", "source-2"));
        when(storyRepository.findTrendingCandidates(
                NOW.minusSeconds(72 * 3600), null, 500)).thenReturn(List.of(story));

        TrendingStoryResponse response = service.trending(10, null, null).get(0);

        assertThat(response.reasons()).containsExactly(
                TrendingReason.RECENTLY_UPDATED,
                TrendingReason.MULTIPLE_SOURCES,
                TrendingReason.MULTIPLE_REPORTS);
        assertThat(TrendingStoryResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("score", "sourceIds", "articleIds", "matchingVersion");
    }

    @Test
    void localizationChangesPresentationButNotRankingAndUsesOneBatchLookup() {
        Story first = story("000000000000000000000002", NOW.minusSeconds(60), 1, Set.of("a"));
        Story second = story("000000000000000000000001", NOW.minusSeconds(120), 1, Set.of("b"));
        Article firstArticle = mock(Article.class);
        Article secondArticle = mock(Article.class);
        when(firstArticle.id()).thenReturn(first.representativeArticleId());
        when(secondArticle.id()).thenReturn(second.representativeArticleId());
        when(storyRepository.findTrendingCandidates(
                NOW.minusSeconds(72 * 3600), null, 500)).thenReturn(List.of(second, first));
        when(articleRepository.findAllById(Set.of(
                first.representativeArticleId(), second.representativeArticleId())))
                .thenReturn(List.of(firstArticle, secondArticle));
        when(localizationService.localize(firstArticle, Language.SI))
                .thenReturn(new LocalizedContentResponse(
                        Language.SI, Language.SI, true, false, "පළමු පුවත", null));
        when(localizationService.localize(secondArticle, Language.SI))
                .thenReturn(new LocalizedContentResponse(
                        Language.SI, Language.SI, true, false, "දෙවන පුවත", null));

        List<TrendingStoryResponse> localized = service.trending(10, null, Language.SI);
        List<TrendingStoryResponse> original = service.trending(10, null, null);

        assertThat(localized).extracting(TrendingStoryResponse::id)
                .containsExactlyElementsOf(original.stream().map(TrendingStoryResponse::id).toList());
        assertThat(localized.get(0).localizedContent().title()).isEqualTo("පළමු පුවත");
        verify(articleRepository).findAllById(org.mockito.ArgumentMatchers.anySet());
    }

    private Story story(String id, Instant lastPublishedAt, long articleCount, Set<String> sourceIds) {
        return new Story(id, "Story " + id, "article-" + id, ArticleCategory.LOCAL,
                lastPublishedAt.minusSeconds(60), lastPublishedAt, articleCount, sourceIds,
                Set.of("article-" + id), lastPublishedAt, lastPublishedAt, "hybrid-v1");
    }
}
