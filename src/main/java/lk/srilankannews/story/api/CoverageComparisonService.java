package lk.srilankannews.story.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleEntity;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.api.ArticleLocalizationService;
import lk.srilankannews.article.api.LocalizedContentResponse;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class CoverageComparisonService {

    private static final Comparator<String> TEXT_ORDER = Comparator.naturalOrder();

    private final StoryRepository storyRepository;
    private final ArticleRepository articleRepository;
    private final SourceService sourceService;
    private final CoverageTextNormalizer normalizer;
    private final ArticleLocalizationService localizationService;

    @Autowired
    public CoverageComparisonService(
            StoryRepository storyRepository,
            ArticleRepository articleRepository,
            SourceService sourceService,
            CoverageTextNormalizer normalizer,
            ArticleLocalizationService localizationService) {
        this.storyRepository = storyRepository;
        this.articleRepository = articleRepository;
        this.sourceService = sourceService;
        this.normalizer = normalizer;
        this.localizationService = localizationService;
    }

    public CoverageComparisonService(
            StoryRepository storyRepository,
            ArticleRepository articleRepository,
            SourceService sourceService,
            CoverageTextNormalizer normalizer) {
        this(storyRepository, articleRepository, sourceService, normalizer, null);
    }

    public CoverageComparisonResponse compare(String storyId) {
        return compare(storyId, null);
    }

    public CoverageComparisonResponse compare(String storyId, Language displayLanguage) {
        Story story = storyRepository.findById(storyId)
                .filter(Story::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Story"));
        Sort articleOrder = Sort.by(
                Sort.Order.asc("publishedAt"),
                Sort.Order.asc("id"));
        List<Article> articles = articleRepository.findByStoryId(storyId, articleOrder);
        Map<String, Source> sourcesById = loadSources(articles);
        List<SourceData> sourceData = groupBySource(articles, sourcesById, displayLanguage);

        Map<String, Integer> topicSourceCounts = sourceCounts(
                sourceData, data -> data.topics.keySet());
        Map<EntityKey, Integer> entitySourceCounts = entitySourceCounts(sourceData);

        List<String> sharedTopics = displayTopics(sourceData, topicSourceCounts, true);
        List<CoverageEntityResponse> sharedEntities = displayEntities(
                sourceData, entitySourceCounts, true);
        List<SourceCoverageResponse> sources = sourceData.stream()
                .map(data -> toResponse(data, topicSourceCounts, entitySourceCounts))
                .sorted(Comparator
                        .comparing(SourceCoverageResponse::firstPublishedAt)
                        .thenComparing(item -> item.source().slug()))
                .toList();

        return new CoverageComparisonResponse(
                story.id(),
                story.canonicalTitle(),
                articles.size(),
                sources.size(),
                sources.size() > 1,
                sharedTopics,
                sharedEntities,
                sources,
                localizedStory(story, articles, displayLanguage));
    }

    private Map<String, Source> loadSources(List<Article> articles) {
        Set<String> sourceIds = articles.stream().map(Article::sourceId).collect(Collectors.toSet());
        if (sourceIds.isEmpty()) {
            return Map.of();
        }
        return sourceService.findAllByIds(sourceIds).stream()
                .collect(Collectors.toUnmodifiableMap(Source::id, Function.identity()));
    }

    private List<SourceData> groupBySource(
            List<Article> articles,
            Map<String, Source> sourcesById,
            Language displayLanguage) {
        Map<String, SourceData> grouped = new LinkedHashMap<>();
        for (Article article : articles) {
            Source source = sourcesById.get(article.sourceId());
            if (source == null) {
                throw new IllegalStateException("Article source attribution is missing.");
            }
            grouped.computeIfAbsent(
                            source.id(), ignored -> new SourceData(source, displayLanguage))
                    .add(article);
        }
        return List.copyOf(grouped.values());
    }

    private Map<String, Integer> sourceCounts(
            List<SourceData> sources,
            Function<SourceData, Set<String>> keys) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        sources.forEach(source -> keys.apply(source)
                .forEach(key -> counts.merge(key, 1, Integer::sum)));
        return counts;
    }

    private Map<EntityKey, Integer> entitySourceCounts(List<SourceData> sources) {
        Map<EntityKey, Integer> counts = new LinkedHashMap<>();
        sources.forEach(source -> source.entities.keySet()
                .forEach(key -> counts.merge(key, 1, Integer::sum)));
        return counts;
    }

    private List<String> displayTopics(
            List<SourceData> sources,
            Map<String, Integer> counts,
            boolean shared) {
        Map<String, String> display = new LinkedHashMap<>();
        sources.forEach(source -> source.topics.forEach(display::putIfAbsent));
        return display.entrySet().stream()
                .filter(entry -> shared == (counts.getOrDefault(entry.getKey(), 0) >= 2))
                .sorted(Map.Entry.comparingByKey(TEXT_ORDER))
                .map(Map.Entry::getValue)
                .toList();
    }

    private List<CoverageEntityResponse> displayEntities(
            List<SourceData> sources,
            Map<EntityKey, Integer> counts,
            boolean shared) {
        Map<EntityKey, CoverageEntityResponse> display = new LinkedHashMap<>();
        sources.forEach(source -> source.entities.forEach(display::putIfAbsent));
        return display.entrySet().stream()
                .filter(entry -> shared == (counts.getOrDefault(entry.getKey(), 0) >= 2))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private SourceCoverageResponse toResponse(
            SourceData data,
            Map<String, Integer> topicSourceCounts,
            Map<EntityKey, Integer> entitySourceCounts) {
        List<String> topics = data.topics.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(TEXT_ORDER))
                .map(Map.Entry::getValue)
                .toList();
        List<String> uniqueTopics = data.topics.entrySet().stream()
                .filter(entry -> topicSourceCounts.getOrDefault(entry.getKey(), 0) == 1)
                .sorted(Map.Entry.comparingByKey(TEXT_ORDER))
                .map(Map.Entry::getValue)
                .toList();
        List<CoverageEntityResponse> entities = data.entities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
        List<CoverageEntityResponse> uniqueEntities = data.entities.entrySet().stream()
                .filter(entry -> entitySourceCounts.getOrDefault(entry.getKey(), 0) == 1)
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
        return new SourceCoverageResponse(
                new CoverageSourceResponse(data.source.name(), data.source.slug()),
                data.articles.size(),
                data.languages.stream().sorted(Comparator.comparing(Language::code)).toList(),
                data.firstPublishedAt,
                data.lastPublishedAt,
                List.copyOf(data.articles),
                topics,
                uniqueTopics,
                entities,
                uniqueEntities);
    }

    private final class SourceData {
        private final Source source;
        private final List<CoverageArticleResponse> articles = new ArrayList<>();
        private final Set<Language> languages = new LinkedHashSet<>();
        private final Map<String, String> topics = new LinkedHashMap<>();
        private final Map<EntityKey, CoverageEntityResponse> entities = new LinkedHashMap<>();
        private Instant firstPublishedAt;
        private Instant lastPublishedAt;
        private final Language displayLanguage;

        private SourceData(Source source, Language displayLanguage) {
            this.source = source;
            this.displayLanguage = displayLanguage;
        }

        private SourceData add(Article article) {
            String summary = article.aiEnrichment() == null
                    ? null
                    : article.aiEnrichment().summary();
            articles.add(new CoverageArticleResponse(
                    article.id(), article.title(), summary, article.originalLanguage(),
                    article.publishedAt(), article.originalUrl(),
                    displayLanguage == null
                            ? null
                            : localizationService.localize(article, displayLanguage)));
            languages.add(article.originalLanguage());
            if (firstPublishedAt == null || article.publishedAt().isBefore(firstPublishedAt)) {
                firstPublishedAt = article.publishedAt();
            }
            if (lastPublishedAt == null || article.publishedAt().isAfter(lastPublishedAt)) {
                lastPublishedAt = article.publishedAt();
            }
            if (article.aiEnrichment() != null) {
                article.aiEnrichment().topics().forEach(this::addTopic);
                article.aiEnrichment().entities().forEach(this::addEntity);
            }
            return this;
        }

        private void addTopic(String topic) {
            String key = normalizer.normalize(topic);
            String display = normalizer.display(topic);
            if (!key.isEmpty() && !display.isEmpty()) {
                topics.putIfAbsent(key, display);
            }
        }

        private void addEntity(ArticleEntity entity) {
            String nameKey = normalizer.normalize(entity.name());
            String typeKey = normalizer.normalize(entity.type());
            String name = normalizer.display(entity.name());
            String type = normalizer.display(entity.type());
            if (!nameKey.isEmpty() && !typeKey.isEmpty() && !name.isEmpty() && !type.isEmpty()) {
                entities.putIfAbsent(
                        new EntityKey(nameKey, typeKey),
                        new CoverageEntityResponse(name, type));
            }
        }
    }

    private LocalizedStoryContentResponse localizedStory(
            Story story, List<Article> articles, Language displayLanguage) {
        if (displayLanguage == null) {
            return null;
        }
        Article representative = articles.stream()
                .filter(article -> article.id().equals(story.representativeArticleId()))
                .findFirst()
                .orElse(null);
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

    private record EntityKey(String name, String type) implements Comparable<EntityKey> {
        @Override
        public int compareTo(EntityKey other) {
            int nameOrder = name.compareTo(other.name);
            return nameOrder != 0 ? nameOrder : type.compareTo(other.type);
        }
    }
}
