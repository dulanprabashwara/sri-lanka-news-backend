package lk.srilankannews.story.api;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.article.api.ArticleLocalizationService;
import lk.srilankannews.article.api.LocalizedContentResponse;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryFilter;
import lk.srilankannews.story.StoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class StoryApiService {

    private final StoryRepository storyRepository;
    private final ArticleRepository articleRepository;
    private final SourceService sourceService;
    private final StoryApiMapper storyApiMapper;
    private final ArticleApiMapper articleApiMapper;
    private final ArticleLocalizationService localizationService;

    @Autowired
    public StoryApiService(
            StoryRepository storyRepository,
            ArticleRepository articleRepository,
            SourceService sourceService,
            StoryApiMapper storyApiMapper,
            ArticleApiMapper articleApiMapper,
            ArticleLocalizationService localizationService) {
        this.storyRepository = storyRepository;
        this.articleRepository = articleRepository;
        this.sourceService = sourceService;
        this.storyApiMapper = storyApiMapper;
        this.articleApiMapper = articleApiMapper;
        this.localizationService = localizationService;
    }

    public StoryApiService(
            StoryRepository storyRepository,
            ArticleRepository articleRepository,
            SourceService sourceService,
            StoryApiMapper storyApiMapper,
            ArticleApiMapper articleApiMapper) {
        this(storyRepository, articleRepository, sourceService, storyApiMapper,
                articleApiMapper, null);
    }

    public PagedResponse<StorySummaryResponse> list(
            int page,
            int size,
            ArticleCategory category,
            Instant publishedFrom,
            Instant publishedTo,
            Sort.Direction direction) {
        return list(page, size, category, publishedFrom, publishedTo, direction, null);
    }

    public PagedResponse<StorySummaryResponse> list(
            int page,
            int size,
            ArticleCategory category,
            Instant publishedFrom,
            Instant publishedTo,
            Sort.Direction direction,
            lk.srilankannews.common.domain.Language displayLanguage) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, "lastPublishedAt").and(Sort.by(direction, "id")));
        Page<Story> stories = storyRepository.findAll(
                new StoryFilter(category, publishedFrom, publishedTo), pageable);
        Map<String, Article> representatives = displayLanguage == null
                ? Map.of()
                : representativeArticles(stories.getContent());
        return PagedResponse.from(stories.map(story -> storyApiMapper.toSummary(
                story, localizedStory(story, representatives.get(story.representativeArticleId()),
                        displayLanguage))));
    }

    public StoryDetailResponse detail(String storyId) {
        return detail(storyId, null);
    }

    public StoryDetailResponse detail(
            String storyId, lk.srilankannews.common.domain.Language displayLanguage) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story"));
        requirePublicStory(story);
        var articleSort = Sort.by(
                Sort.Order.desc("publishedAt"),
                Sort.Order.asc("id"));
        var articles = articleRepository.findByStoryId(storyId, articleSort);
        Map<String, Source> sourcesById = sourcesById(articles);
        var responses = articles.stream()
                .map(article -> articleApiMapper.toResponse(
                        article, sourceFor(article, sourcesById), displayLanguage))
                .toList();
        Article representative = articles.stream()
                .filter(article -> article.id().equals(story.representativeArticleId()))
                .findFirst()
                .orElseGet(() -> articleRepository.findById(story.representativeArticleId())
                        .orElse(null));
        return storyApiMapper.toDetail(
                story, responses, localizedStory(story, representative, displayLanguage));
    }

    public StorySummaryResponse storyForArticle(String articleId) {
        return storyForArticle(articleId, null);
    }

    public StorySummaryResponse storyForArticle(
            String articleId, lk.srilankannews.common.domain.Language displayLanguage) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article"));
        if (article.storyId() == null) {
            throw new ResourceNotFoundException("Story");
        }
        Story story = storyRepository.findById(article.storyId())
                .orElseThrow(() -> new ResourceNotFoundException("Story"));
        requirePublicStory(story);
        Article representative = displayLanguage == null
                ? null
                : article.id().equals(story.representativeArticleId())
                        ? article
                        : articleRepository.findById(story.representativeArticleId()).orElse(null);
        return storyApiMapper.toSummary(
                story, localizedStory(story, representative, displayLanguage));
    }

    private Map<String, Article> representativeArticles(java.util.List<Story> stories) {
        Set<String> ids = stories.stream()
                .map(Story::representativeArticleId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return java.util.stream.StreamSupport.stream(
                        articleRepository.findAllById(ids).spliterator(), false)
                .collect(Collectors.toUnmodifiableMap(Article::id, Function.identity()));
    }

    private LocalizedStoryContentResponse localizedStory(
            Story story,
            Article representative,
            lk.srilankannews.common.domain.Language displayLanguage) {
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

    private void requirePublicStory(Story story) {
        if (!story.isPubliclyVisible()) {
            throw new ResourceNotFoundException("Story");
        }
    }

    private Map<String, Source> sourcesById(java.util.List<Article> articles) {
        Set<String> sourceIds = articles.stream().map(Article::sourceId).collect(Collectors.toSet());
        if (sourceIds.isEmpty()) {
            return Map.of();
        }
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
}
