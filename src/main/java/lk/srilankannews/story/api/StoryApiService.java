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

@Service
public class StoryApiService {

    private final StoryRepository storyRepository;
    private final ArticleRepository articleRepository;
    private final SourceService sourceService;
    private final StoryApiMapper storyApiMapper;
    private final ArticleApiMapper articleApiMapper;

    public StoryApiService(
            StoryRepository storyRepository,
            ArticleRepository articleRepository,
            SourceService sourceService,
            StoryApiMapper storyApiMapper,
            ArticleApiMapper articleApiMapper) {
        this.storyRepository = storyRepository;
        this.articleRepository = articleRepository;
        this.sourceService = sourceService;
        this.storyApiMapper = storyApiMapper;
        this.articleApiMapper = articleApiMapper;
    }

    public PagedResponse<StorySummaryResponse> list(
            int page,
            int size,
            ArticleCategory category,
            Instant publishedFrom,
            Instant publishedTo,
            Sort.Direction direction) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, "lastPublishedAt").and(Sort.by(direction, "id")));
        Page<Story> stories = storyRepository.findAll(
                new StoryFilter(category, publishedFrom, publishedTo), pageable);
        return PagedResponse.from(stories.map(storyApiMapper::toSummary));
    }

    public StoryDetailResponse detail(String storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story"));
        requirePublicStory(story);
        var articleSort = Sort.by(
                Sort.Order.desc("publishedAt"),
                Sort.Order.asc("id"));
        var articles = articleRepository.findByStoryId(storyId, articleSort);
        Map<String, Source> sourcesById = sourcesById(articles);
        var responses = articles.stream()
                .map(article -> articleApiMapper.toResponse(article, sourceFor(article, sourcesById)))
                .toList();
        return storyApiMapper.toDetail(story, responses);
    }

    public StorySummaryResponse storyForArticle(String articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article"));
        if (article.storyId() == null) {
            throw new ResourceNotFoundException("Story");
        }
        Story story = storyRepository.findById(article.storyId())
                .orElseThrow(() -> new ResourceNotFoundException("Story"));
        requirePublicStory(story);
        return storyApiMapper.toSummary(story);
    }

    private void requirePublicStory(Story story) {
        if (story.articleCount() < 1) {
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
