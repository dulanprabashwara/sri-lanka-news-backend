package lk.srilankannews.article.trending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TrendingArticleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ArticleRepository articleRepository;
    private StoryRepository storyRepository;
    private SourceService sourceService;
    private ArticleApiMapper articleApiMapper;
    private TrendingArticleProperties properties;
    private TrendingArticleService service;

    @BeforeEach
    void setUp() {
        articleRepository = mock(ArticleRepository.class);
        storyRepository = mock(StoryRepository.class);
        sourceService = mock(SourceService.class);
        articleApiMapper = mock(ArticleApiMapper.class);
        properties = new TrendingArticleProperties(
                Duration.ofHours(48), Duration.ofHours(8), 200, 20, 50, 2, 3, Duration.ofSeconds(90));

        service = new TrendingArticleService(
                articleRepository, storyRepository, sourceService, articleApiMapper,
                properties, FIXED_CLOCK, null, new ObjectMapper());

        when(sourceService.findAllByIds(any())).thenAnswer(invocation -> {
            Set<String> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(id -> new Source(id, "Source " + id, "src-" + id, "https://" + id + ".com",
                            Language.EN, IngestionType.RSS, true, NOW, NOW))
                    .toList();
        });

        when(articleApiMapper.toResponse(any(), any(), any())).thenAnswer(invocation -> {
            Article a = invocation.getArgument(0);
            Source s = invocation.getArgument(1);
            Language l = invocation.getArgument(2);
            return new ArticleResponse(
                    a.id(), a.title(), a.originalUrl(), a.originalLanguage(), a.authors(),
                    a.publishedAt(), a.discoveredAt(), a.category(), a.summary(), List.of(),
                    null, null, null);
        });
    }

    @Test
    void recentStandaloneArticleWithoutAiEnrichmentOrPublicStoryIsEligible() {
        Article singleton = article("art-1", "src-1", "Standalone breaking report",
                NOW.minus(Duration.ofHours(2)), null);
        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(singleton));

        List<ArticleResponse> result = service.trending(20, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("art-1");
    }

    @Test
    void newerArticleOutranksOlderArticleDueToRecencyDecay() {
        Article older = article("art-old", "src-1", "Older report",
                NOW.minus(Duration.ofHours(24)), null);
        Article newer = article("art-new", "src-1", "Newer report",
                NOW.minus(Duration.ofHours(2)), null);
        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(older, newer));

        List<ArticleResponse> result = service.trending(20, null, null);

        assertThat(result).extracting(ArticleResponse::id)
                .containsExactly("art-new", "art-old");
    }

    @Test
    void articleOutsideLookbackWindowIsExcluded() {
        Article ancient = article("art-ancient", "src-1", "Ancient report",
                NOW.minus(Duration.ofHours(50)), null);
        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(ancient));

        List<ArticleResponse> result = service.trending(20, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void relatedReportingActivityIncreasesScore() {
        Instant pubTime = NOW.minus(Duration.ofHours(4));
        Article standalone = article("art-single", "src-1", "Single report", pubTime, null);
        Article clustered = article("art-cluster", "src-2", "Clustered report", pubTime, "story-1");

        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(standalone, clustered));

        List<Article> recentStoryArticles = List.of(
                article("art-c1", "src-2", "Title 1", pubTime, "story-1"),
                article("art-c2", "src-2", "Title 2", pubTime, "story-1"),
                article("art-c3", "src-2", "Title 3", pubTime, "story-1"),
                article("art-c4", "src-2", "Title 4", pubTime, "story-1"),
                article("art-c5", "src-2", "Title 5", pubTime, "story-1"),
                article("art-c6", "src-2", "Title 6", pubTime, "story-1")
        );
        when(articleRepository.findRecentStoryArticles(eq(Set.of("story-1")), any()))
                .thenReturn(recentStoryArticles);

        List<ArticleResponse> result = service.trending(20, null, null);

        assertThat(result).extracting(ArticleResponse::id)
                .containsExactly("art-cluster", "art-single");
    }

    @Test
    void multipleDistinctPublishersIncreaseScoreMoreThanSinglePublisher() {
        Instant pubTime = NOW.minus(Duration.ofHours(4));
        Article singlePublisherArt = article("art-single-pub", "src-1", "Headline", pubTime, "story-single-pub");
        Article multiPublisherArt = article("art-multi-pub", "src-2", "Headline", pubTime, "story-multi-pub");

        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(singlePublisherArt, multiPublisherArt));

        List<Article> recentStoryArticles = List.of(
                article("art-s1", "src-1", "Headline", pubTime, "story-single-pub"),
                article("art-s2", "src-1", "Headline", pubTime, "story-single-pub"),
                article("art-m1", "src-1", "Headline", pubTime, "story-multi-pub"),
                article("art-m2", "src-2", "Headline", pubTime, "story-multi-pub"),
                article("art-m3", "src-3", "Headline", pubTime, "story-multi-pub"),
                article("art-m4", "src-4", "Headline", pubTime, "story-multi-pub")
        );
        when(articleRepository.findRecentStoryArticles(any(), any()))
                .thenReturn(recentStoryArticles);

        List<ArticleResponse> result = service.trending(20, null, null);

        assertThat(result).extracting(ArticleResponse::id)
                .containsExactly("art-multi-pub", "art-single-pub");
    }

    @Test
    void recentReportingBurstIncreasesVelocityScore() {
        Instant pubTime = NOW.minus(Duration.ofHours(4));
        Article slowBurstArt = article("art-slow", "src-1", "Headline", pubTime, "story-slow");
        Article fastBurstArt = article("art-fast", "src-2", "Headline", pubTime, "story-fast");

        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(slowBurstArt, fastBurstArt));

        List<Article> recentStoryArticles = List.of(
                article("art-slow-1", "src-1", "Headline", NOW.minus(Duration.ofHours(30)), "story-slow"),
                article("art-slow-2", "src-1", "Headline", pubTime, "story-slow"),
                article("art-fast-1", "src-2", "Headline", NOW.minus(Duration.ofHours(5)), "story-fast"),
                article("art-fast-2", "src-2", "Headline", pubTime, "story-fast")
        );
        when(articleRepository.findRecentStoryArticles(any(), any()))
                .thenReturn(recentStoryArticles);

        List<ArticleResponse> result = service.trending(20, null, null);

        assertThat(result).extracting(ArticleResponse::id)
                .containsExactly("art-fast", "art-slow");
    }

    @Test
    void diversityFilterEnforcesMaxPerStoryAndMaxPerSourceAndFillsRemaining() {
        Instant time = NOW.minus(Duration.ofHours(1));
        Article s1_a1 = article("s1-1", "src-1", "Title 1", time, "story-1");
        Article s1_a2 = article("s1-2", "src-1", "Title 2", time.minus(Duration.ofMinutes(1)), "story-1");
        Article s1_a3 = article("s1-3", "src-1", "Title 3", time.minus(Duration.ofMinutes(2)), "story-1"); // 3rd from story-1 -> skipped (max 2)
        Article s2_a1 = article("s2-1", "src-1", "Title 4", time.minus(Duration.ofMinutes(3)), "story-2"); // 3rd accepted from src-1 -> accepted
        Article s2_a2 = article("s2-2", "src-1", "Title 5", time.minus(Duration.ofMinutes(4)), "story-2"); // 4th from src-1 -> skipped (max 3)
        Article s3_a1 = article("s3-1", "src-2", "Title 6", time.minus(Duration.ofMinutes(5)), "story-3"); // from src-2 -> selected!

        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(s1_a1, s1_a2, s1_a3, s2_a1, s2_a2, s3_a1));

        List<ArticleResponse> result = service.trending(10, null, null);

        assertThat(result).extracting(ArticleResponse::id)
                .containsExactly("s1-1", "s1-2", "s2-1", "s3-1");
    }

    @Test
    void oldArticlesOutsideLookbackDoNotBoostStoryScore() {
        Instant pubTime = NOW.minus(Duration.ofHours(4));
        Article standalone = article("art-single", "src-1", "Headline", pubTime, null);
        Article oldStoryArt = article("art-old-story", "src-2", "Headline", pubTime, "story-old");

        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(standalone, oldStoryArt));

        // Within lookback window, only 1 recent article exists for story-old
        when(articleRepository.findRecentStoryArticles(eq(Set.of("story-old")), any()))
                .thenReturn(List.of(oldStoryArt));

        List<ArticleResponse> result = service.trending(20, null, null);

        // Tie-breaker by id ASC ("art-old-story" vs "art-single") because both have identical 0 activity/diversity boost
        assertThat(result).extracting(ArticleResponse::id)
                .containsExactly("art-old-story", "art-single");
    }

    @Test
    void trueHalfLifeFormulaGivesExactValues() {
        // At age=0h -> recency = 65.0
        // At age=8h (1 half-life) -> recency = 32.5
        // At age=16h (2 half-lives) -> recency = 16.25
        // At age=24h (3 half-lives) -> recency = 8.125
        Article a0 = article("a-0h", "src-1", "T0", NOW, null);
        Article a8 = article("a-8h", "src-2", "T8", NOW.minus(Duration.ofHours(8)), null);
        Article a16 = article("a-16h", "src-3", "T16", NOW.minus(Duration.ofHours(16)), null);
        Article a24 = article("a-24h", "src-4", "T24", NOW.minus(Duration.ofHours(24)), null);

        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(a24, a16, a8, a0));

        List<ArticleResponse> result = service.trending(10, null, null);

        assertThat(result).extracting(ArticleResponse::id)
                .containsExactly("a-0h", "a-8h", "a-16h", "a-24h");
    }

    @Test
    void smallFutureClockSkewClampedToRankingNow() {
        Article futureSkew = article("art-skew", "src-1", "Headline",
                NOW.plus(Duration.ofMinutes(5)), null);
        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(futureSkew));

        List<ArticleResponse> result = service.trending(10, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("art-skew");
    }

    @Test
    void farFuturePublishedAtFallsBackToDiscoveredAt() {
        // Published at 5 days in future (clearly invalid), but discovered 10 hours ago
        Article invalidFuture = new Article(
                "art-future", "src-1", "Headline", "https://example.com/future",
                "https://example.com/future", Language.EN, List.of(), NOW.plus(Duration.ofDays(5)),
                NOW.minus(Duration.ofHours(10)), ArticleCategory.LOCAL, "content", "hash-future",
                null, ProcessingStatus.COMPLETED, null, NOW.minus(Duration.ofHours(10)), NOW);

        // Valid article published 2 hours ago
        Article normal = article("art-normal", "src-2", "Normal", NOW.minus(Duration.ofHours(2)), null);

        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(invalidFuture, normal));

        List<ArticleResponse> result = service.trending(10, null, null);

        // normal (age 2h) must outrank invalidFuture (falls back to discoveredAt age 10h)
        assertThat(result).extracting(ArticleResponse::id)
                .containsExactly("art-normal", "art-future");
    }

    @Test
    void missingPublishedAtFallsBackToDiscoveredAt() {
        Article fallbackArt = new Article(
                "art-fallback", "src-1", "Headline", "https://example.com/fallback",
                "https://example.com/fallback", Language.EN, List.of(), null,
                NOW.minus(Duration.ofHours(3)), ArticleCategory.LOCAL, "content", "hash",
                ProcessingStatus.COMPLETED, NOW.minus(Duration.ofHours(3)), NOW);

        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(fallbackArt));

        List<ArticleResponse> result = service.trending(10, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("art-fallback");
    }

    @Test
    void blankTitleArticleExcluded() {
        Article blankTitle = article("art-blank", "src-1", "   ", NOW.minus(Duration.ofHours(1)), null);
        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(blankTitle));

        List<ArticleResponse> result = service.trending(10, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void limitParameterEnforcedAndBounded() {
        List<Article> articles = List.of(
                article("a1", "s1", "T1", NOW.minus(Duration.ofMinutes(1)), null),
                article("a2", "s2", "T2", NOW.minus(Duration.ofMinutes(2)), null),
                article("a3", "s3", "T3", NOW.minus(Duration.ofMinutes(3)), null)
        );
        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(articles);

        List<ArticleResponse> result = service.trending(2, null, null);

        assertThat(result).hasSize(2);
    }

    @Test
    void redisExceptionFallsBackGracefullyToMongoCalculation() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(any())).thenThrow(new RuntimeException("Redis connection refused"));

        TrendingArticleService resilientService = new TrendingArticleService(
                articleRepository, storyRepository, sourceService, articleApiMapper,
                properties, FIXED_CLOCK, redis, new ObjectMapper());

        Article article = article("a1", "s1", "Title", NOW.minus(Duration.ofHours(1)), null);
        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(article));

        List<ArticleResponse> result = resilientService.trending(10, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("a1");
    }

    @Test
    void displayLanguageForwardedToApiMapper() {
        Article article = article("a1", "s1", "Title", NOW.minus(Duration.ofHours(1)), null);
        when(articleRepository.findTrendingCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(article));

        service.trending(10, null, Language.SI);

        verify(articleApiMapper).toResponse(any(), any(), eq(Language.SI));
    }

    private static Article article(String id, String sourceId, String title, Instant pub, String storyId) {
        return new Article(
                id, sourceId, title, "https://example.com/" + id,
                "https://example.com/" + id, Language.EN, List.of(), pub,
                pub, ArticleCategory.LOCAL, "content", "hash-" + id,
                null, ProcessingStatus.COMPLETED, storyId, pub, pub);
    }
}
