package lk.srilankannews.story.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class StoryTimelineService {

    private static final Comparator<Article> ARTICLE_ORDER = Comparator
            .comparing(Article::publishedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Article::id);

    private final StoryRepository storyRepository;
    private final ArticleRepository articleRepository;
    private final SourceService sourceService;

    public StoryTimelineService(
            StoryRepository storyRepository,
            ArticleRepository articleRepository,
            SourceService sourceService) {
        this.storyRepository = storyRepository;
        this.articleRepository = articleRepository;
        this.sourceService = sourceService;
    }

    public StoryTimelineResponse timeline(String storyId) {
        Story story = storyRepository.findById(storyId)
                .filter(candidate -> candidate.articleCount() > 0)
                .orElseThrow(() -> new ResourceNotFoundException("Story"));
        Sort requestedOrder = Sort.by(
                Sort.Order.asc("publishedAt"),
                Sort.Order.asc("id"));
        List<Article> articles = articleRepository.findByStoryId(storyId, requestedOrder)
                .stream()
                .sorted(ARTICLE_ORDER)
                .toList();
        if (articles.isEmpty()) {
            throw new IllegalStateException("Public Story has no assigned Articles.");
        }
        if (articles.stream().anyMatch(article -> article.publishedAt() == null)) {
            throw new IllegalStateException("Timeline Article publication time is missing.");
        }

        Map<String, Source> sourcesById = loadSources(articles);
        Instant firstPublishedAt = articles.get(0).publishedAt();
        Instant lastPublishedAt = articles.get(articles.size() - 1).publishedAt();
        List<TimelineEventResponse> events = articles.stream()
                .map(article -> toEvent(article, sourceFor(article, sourcesById), firstPublishedAt))
                .toList();

        return new StoryTimelineResponse(
                story.id(),
                story.canonicalTitle(),
                firstPublishedAt,
                lastPublishedAt,
                events.size(),
                sourcesById.size(),
                events);
    }

    private Map<String, Source> loadSources(List<Article> articles) {
        Set<String> sourceIds = articles.stream().map(Article::sourceId).collect(Collectors.toSet());
        return sourceService.findAllByIds(sourceIds).stream()
                .collect(Collectors.toUnmodifiableMap(Source::id, Function.identity()));
    }

    private Source sourceFor(Article article, Map<String, Source> sourcesById) {
        Source source = sourcesById.get(article.sourceId());
        if (source == null) {
            throw new IllegalStateException("Article source attribution is missing.");
        }
        return source;
    }

    private TimelineEventResponse toEvent(Article article, Source source, Instant first) {
        String summary = article.aiEnrichment() == null
                ? null
                : article.aiEnrichment().summary();
        return new TimelineEventResponse(
                article.id(),
                article.title(),
                summary,
                article.originalLanguage(),
                article.publishedAt(),
                article.originalUrl(),
                new TimelineSourceResponse(source.name(), source.slug()),
                Duration.between(first, article.publishedAt()).toMinutes());
    }
}
