package lk.srilankannews.article.trending;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TrendingArticleService {

    static final double MAX_RECENCY_SCORE = 65.0;
    static final double MAX_ACTIVITY_SCORE = 20.0;
    static final double MAX_DIVERSITY_SCORE = 10.0;
    static final double MAX_VELOCITY_SCORE = 5.0;
    private static final double LN_2 = Math.log(2.0);
    private static final Duration MAX_FUTURE_CLOCK_SKEW = Duration.ofMinutes(15);

    private static final Logger LOGGER = LoggerFactory.getLogger(TrendingArticleService.class);
    private static final TypeReference<List<ArticleResponse>> RESPONSE_TYPE = new TypeReference<>() {};

    private final ArticleRepository articleRepository;
    private final StoryRepository storyRepository;
    private final SourceService sourceService;
    private final ArticleApiMapper articleApiMapper;
    private final TrendingArticleProperties properties;
    private final Clock clock;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Autowired
    public TrendingArticleService(
            ArticleRepository articleRepository,
            StoryRepository storyRepository,
            SourceService sourceService,
            ArticleApiMapper articleApiMapper,
            TrendingArticleProperties properties,
            Clock clock,
            @Autowired(required = false) StringRedisTemplate redis,
            ObjectMapper objectMapper) {
        this.articleRepository = articleRepository;
        this.storyRepository = storyRepository;
        this.sourceService = sourceService;
        this.articleApiMapper = articleApiMapper;
        this.properties = properties;
        this.clock = clock;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public List<ArticleResponse> trending(int limit, ArticleCategory category, Language displayLanguage) {
        int effectiveLimit = Math.min(
                limit > 0 ? limit : properties.defaultLimit(),
                properties.maxLimit());

        String cacheKey = cacheKey(effectiveLimit, category, displayLanguage);
        Optional<List<ArticleResponse>> cached = readFromCache(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<ArticleResponse> result = computeTrending(effectiveLimit, category, displayLanguage);
        writeToCache(cacheKey, result);
        return result;
    }

    private List<ArticleResponse> computeTrending(
            int limit, ArticleCategory category, Language displayLanguage) {
        Instant rankingNow = clock.instant();
        Instant publishedSince = rankingNow.minus(properties.lookbackWindow());

        List<Article> candidates = articleRepository.findTrendingCandidates(
                publishedSince, category, properties.candidateLimit());

        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<String> storyIds = candidates.stream()
                .map(Article::storyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, StoryRecentActivity> storyActivities = computeStoryActivities(
                storyIds, publishedSince, rankingNow);

        List<RankedArticle> ranked = new ArrayList<>();
        for (Article article : candidates) {
            if (article.title() == null || article.title().isBlank()) {
                continue;
            }
            Instant effectiveTime = resolveEffectiveTime(article, rankingNow);
            if (effectiveTime == null) {
                continue;
            }
            double ageHours = Math.max(0.0,
                    Duration.between(effectiveTime, rankingNow).toMillis() / 3_600_000.0);
            if (ageHours > properties.lookbackWindow().toHours()) {
                continue;
            }

            StoryRecentActivity activity = article.storyId() != null ? storyActivities.get(article.storyId()) : null;
            double score = calculateScore(ageHours, activity);
            ranked.add(new RankedArticle(article, score, effectiveTime));
        }

        ranked.sort(Comparator.comparingDouble(RankedArticle::score).reversed()
                .thenComparing(RankedArticle::effectiveTime, Comparator.reverseOrder())
                .thenComparing(ra -> ra.article().id()));

        List<Article> selected = applyDiversityFilter(ranked, limit);

        Set<String> sourceIds = selected.stream()
                .map(Article::sourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, Source> sourcesById = sourceIds.isEmpty()
                ? Map.of()
                : sourceService.findAllByIds(sourceIds).stream()
                        .collect(Collectors.toMap(Source::id, Function.identity()));

        return selected.stream()
                .map(article -> {
                    Source src = sourcesById.get(article.sourceId());
                    return articleApiMapper.toResponse(article, src, displayLanguage);
                })
                .toList();
    }

    private double calculateScore(double ageHours, StoryRecentActivity activity) {
        double halfLifeHours = Math.max(0.1, properties.halfLife().toHours());
        double recencyRatio = Math.exp(-LN_2 * ageHours / halfLifeHours);
        double recencyScore = MAX_RECENCY_SCORE * recencyRatio;

        double activityScore = 0.0;
        double diversityScore = 0.0;
        double velocityScore = 0.0;

        if (activity != null) {
            long reports = Math.max(1, activity.reportCount());
            if (reports > 1) {
                double activityRatio = Math.min(1.0, (reports - 1) / 5.0);
                activityScore = MAX_ACTIVITY_SCORE * activityRatio;
            }

            int publishers = Math.max(1, activity.sourceCount());
            if (publishers > 1) {
                double diversityRatio = Math.min(1.0, (publishers - 1) / 3.0);
                diversityScore = MAX_DIVERSITY_SCORE * diversityRatio;
            }

            if (activity.reportCount() > 1 && activity.firstPublishedAt() != null && activity.lastPublishedAt() != null) {
                double spanHours = Math.max(0.5,
                        Duration.between(activity.firstPublishedAt(), activity.lastPublishedAt()).toMillis() / 3_600_000.0);
                double reportsPerHour = (activity.reportCount() - 1) / spanHours;
                double velocityRatio = Math.min(1.0, reportsPerHour / 2.0);
                velocityScore = MAX_VELOCITY_SCORE * velocityRatio;
            }
        }

        return recencyScore + activityScore + diversityScore + velocityScore;
    }

    record StoryRecentActivity(
            int reportCount,
            int sourceCount,
            Instant firstPublishedAt,
            Instant lastPublishedAt) {}

    private Map<String, StoryRecentActivity> computeStoryActivities(
            Set<String> storyIds, Instant publishedSince, Instant rankingNow) {
        if (storyIds.isEmpty()) {
            return Map.of();
        }
        List<Article> recentArticles = articleRepository.findRecentStoryArticles(storyIds, publishedSince);
        Map<String, List<Article>> byStory = recentArticles.stream()
                .filter(a -> a.storyId() != null)
                .collect(Collectors.groupingBy(Article::storyId));

        Map<String, StoryRecentActivity> activities = new HashMap<>();
        for (Map.Entry<String, List<Article>> entry : byStory.entrySet()) {
            List<Article> articles = entry.getValue();
            int reportCount = articles.size();
            int sourceCount = (int) articles.stream()
                    .map(Article::sourceId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            Instant firstPublished = null;
            Instant lastPublished = null;
            for (Article a : articles) {
                Instant t = resolveEffectiveTime(a, rankingNow);
                if (t != null) {
                    if (firstPublished == null || t.isBefore(firstPublished)) {
                        firstPublished = t;
                    }
                    if (lastPublished == null || t.isAfter(lastPublished)) {
                        lastPublished = t;
                    }
                }
            }
            activities.put(entry.getKey(), new StoryRecentActivity(
                    reportCount, sourceCount, firstPublished, lastPublished));
        }
        return activities;
    }

    private List<Article> applyDiversityFilter(List<RankedArticle> ranked, int limit) {
        Map<String, Integer> storyCounts = new HashMap<>();
        Map<String, Integer> sourceCounts = new HashMap<>();
        List<Article> selected = new ArrayList<>();

        for (RankedArticle candidate : ranked) {
            if (selected.size() >= limit) {
                break;
            }
            String storyId = candidate.article().storyId();
            String sourceId = candidate.article().sourceId();

            if (storyId != null && storyCounts.getOrDefault(storyId, 0) >= properties.maxPerStory()) {
                continue;
            }
            if (sourceId != null && sourceCounts.getOrDefault(sourceId, 0) >= properties.maxPerSource()) {
                continue;
            }

            selected.add(candidate.article());
            if (storyId != null) {
                storyCounts.put(storyId, storyCounts.getOrDefault(storyId, 0) + 1);
            }
            if (sourceId != null) {
                sourceCounts.put(sourceId, sourceCounts.getOrDefault(sourceId, 0) + 1);
            }
        }
        return selected;
    }

    private Instant resolveEffectiveTime(Article article, Instant rankingNow) {
        Instant maxAllowedFuture = rankingNow.plus(MAX_FUTURE_CLOCK_SKEW);
        Instant time = null;

        if (article.publishedAt() != null) {
            if (!article.publishedAt().isAfter(maxAllowedFuture)) {
                time = article.publishedAt();
            } else {
                LOGGER.warn("article_far_future_published_at articleId={} publishedAt={}",
                        article.id(), article.publishedAt());
            }
        }

        if (time == null && article.discoveredAt() != null) {
            if (!article.discoveredAt().isAfter(maxAllowedFuture)) {
                time = article.discoveredAt();
            }
        }

        if (time == null) {
            time = article.createdAt();
        }

        if (time != null && time.isAfter(rankingNow)) {
            time = rankingNow;
        }

        return time;
    }

    private String cacheKey(int limit, ArticleCategory category, Language displayLanguage) {
        return String.format(
                "news:trending:articles:cat=%s:lang=%s:limit=%d",
                category != null ? category.name() : "all",
                displayLanguage != null ? displayLanguage.code() : "none",
                limit);
    }

    private Optional<List<ArticleResponse>> readFromCache(String key) {
        if (redis == null) {
            return Optional.empty();
        }
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return Optional.of(objectMapper.readValue(cached, RESPONSE_TYPE));
            }
        } catch (Exception e) {
            LOGGER.warn("trending_articles_cache_read_failed reason={}", e.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    private void writeToCache(String key, List<ArticleResponse> result) {
        if (redis == null || result == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(result);
            redis.opsForValue().set(key, payload, properties.cacheTtl());
        } catch (Exception e) {
            LOGGER.warn("trending_articles_cache_write_failed reason={}", e.getClass().getSimpleName());
        }
    }

    record RankedArticle(Article article, double score, Instant effectiveTime) {}
}
