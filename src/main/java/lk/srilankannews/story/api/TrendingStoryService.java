package lk.srilankannews.story.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.api.ArticleLocalizationService;
import lk.srilankannews.article.api.LocalizedContentResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import lk.srilankannews.story.TrendingProperties;
import org.springframework.stereotype.Service;

@Service
public class TrendingStoryService {

    static final double RECENCY_WEIGHT = 0.60;
    static final double SOURCE_WEIGHT = 0.25;
    static final double REPORT_WEIGHT = 0.15;

    private final StoryRepository storyRepository;
    private final ArticleRepository articleRepository;
    private final ArticleLocalizationService localizationService;
    private final TrendingProperties properties;
    private final Clock clock;

    public TrendingStoryService(
            StoryRepository storyRepository,
            ArticleRepository articleRepository,
            ArticleLocalizationService localizationService,
            TrendingProperties properties,
            Clock clock) {
        this.storyRepository = storyRepository;
        this.articleRepository = articleRepository;
        this.localizationService = localizationService;
        this.properties = properties;
        this.clock = clock;
    }

    public List<TrendingStoryResponse> trending(
            int limit, ArticleCategory category, Language displayLanguage) {
        Instant now = clock.instant();
        Instant publishedSince = now.minus(Duration.ofHours(properties.windowHours()));
        List<Story> candidates = storyRepository.findTrendingCandidates(
                publishedSince, category, properties.maxCandidates());
        List<RankedStory> ranked = candidates.stream()
                .map(story -> new RankedStory(story, score(story, now)))
                .sorted(ranking())
                .limit(limit)
                .toList();
        Map<String, Article> representatives = displayLanguage == null
                ? Map.of()
                : representativeArticles(ranked);
        return ranked.stream()
                .map(item -> response(item.story(), now, displayLanguage,
                        representatives.get(item.story().representativeArticleId())))
                .toList();
    }

    private double score(Story story, Instant now) {
        double ageHours = Math.max(0.0,
                Duration.between(story.lastPublishedAt(), now).toMillis() / 3_600_000.0);
        double recency = Math.exp(-ageHours / properties.recencyHalfLifeHours());
        double sourceCoverage = Math.min(
                story.sourceIds().size() / properties.sourceNormalization(), 1.0);
        double reportCoverage = Math.min(
                story.articleCount() / properties.reportNormalization(), 1.0);
        return RECENCY_WEIGHT * recency
                + SOURCE_WEIGHT * sourceCoverage
                + REPORT_WEIGHT * reportCoverage;
    }

    private Comparator<RankedStory> ranking() {
        return Comparator.comparingDouble(RankedStory::score).reversed()
                .thenComparing(item -> item.story().lastPublishedAt(), Comparator.reverseOrder())
                .thenComparing(item -> item.story().articleCount(), Comparator.reverseOrder())
                .thenComparing(item -> item.story().id(), Comparator.reverseOrder());
    }

    private TrendingStoryResponse response(
            Story story, Instant now, Language displayLanguage, Article representative) {
        return new TrendingStoryResponse(
                story.id(),
                story.canonicalTitle(),
                story.category(),
                story.firstPublishedAt(),
                story.lastPublishedAt(),
                story.articleCount(),
                story.sourceIds().size(),
                localizedStory(story, representative, displayLanguage),
                reasons(story, now));
    }

    private List<TrendingReason> reasons(Story story, Instant now) {
        List<TrendingReason> reasons = new ArrayList<>();
        Instant recentBoundary = now.minus(Duration.ofMillis(
                Math.round(properties.recencyHalfLifeHours() * 3_600_000.0)));
        if (!story.lastPublishedAt().isBefore(recentBoundary)) {
            reasons.add(TrendingReason.RECENTLY_UPDATED);
        }
        if (story.sourceIds().size() >= 2) {
            reasons.add(TrendingReason.MULTIPLE_SOURCES);
        }
        if (story.articleCount() >= 2) {
            reasons.add(TrendingReason.MULTIPLE_REPORTS);
        }
        return reasons;
    }

    private Map<String, Article> representativeArticles(List<RankedStory> ranked) {
        Set<String> ids = ranked.stream()
                .map(item -> item.story().representativeArticleId())
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return StreamSupport.stream(articleRepository.findAllById(ids).spliterator(), false)
                .collect(Collectors.toUnmodifiableMap(Article::id, Function.identity()));
    }

    private LocalizedStoryContentResponse localizedStory(
            Story story, Article representative, Language displayLanguage) {
        if (displayLanguage == null) {
            return null;
        }
        if (representative == null) {
            return new LocalizedStoryContentResponse(
                    displayLanguage, displayLanguage, false, true, story.canonicalTitle());
        }
        LocalizedContentResponse localized = localizationService.localize(
                representative, displayLanguage);
        return new LocalizedStoryContentResponse(
                localized.requestedLanguage(), localized.resolvedLanguage(),
                localized.translated(), localized.fallback(), localized.title());
    }

    private record RankedStory(Story story, double score) {
    }
}
