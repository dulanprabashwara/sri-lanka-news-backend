package lk.srilankannews.user;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.article.api.ArticleLocalizationService;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import lk.srilankannews.story.api.LocalizedStoryContentResponse;
import lk.srilankannews.story.api.StoryApiMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lk.srilankannews.analytics.AnalyticsRecorder;
import lk.srilankannews.analytics.AnalyticsEventType;
import org.springframework.stereotype.Service;

@Service
public class UserBookmarkService {
    private final UserBookmarkRepository repository;
    private final ArticleRepository articleRepository;
    private final StoryRepository storyRepository;
    private final SourceService sourceService;
    private final ArticleApiMapper articleApiMapper;
    private final StoryApiMapper storyApiMapper;
    private final ArticleLocalizationService localizationService;
    private final Clock clock;
    private final AnalyticsRecorder analyticsRecorder;

    public UserBookmarkService(
            UserBookmarkRepository repository,
            ArticleRepository articleRepository,
            StoryRepository storyRepository,
            SourceService sourceService,
            ArticleApiMapper articleApiMapper,
            StoryApiMapper storyApiMapper,
            ArticleLocalizationService localizationService,
            Clock clock,
            AnalyticsRecorder analyticsRecorder) {
        this.repository = repository;
        this.articleRepository = articleRepository;
        this.storyRepository = storyRepository;
        this.sourceService = sourceService;
        this.articleApiMapper = articleApiMapper;
        this.storyApiMapper = storyApiMapper;
        this.localizationService = localizationService;
        this.clock = clock;
        this.analyticsRecorder = analyticsRecorder;
    }

    public BookmarkStatusResponse create(String userId, BookmarkTargetType type, String targetId) {
        requireTarget(type, targetId);
        UserBookmark bookmark;
        try {
            bookmark = repository.save(UserBookmark.create(userId, type, targetId, clock.instant()));
        } catch (DuplicateKeyException exception) {
            bookmark = repository.findByUserIdAndTargetTypeAndTargetId(userId, type, targetId)
                    .orElseThrow(() -> exception);
        }
        
        analyticsRecorder.recordBestEffort(AnalyticsEventType.BOOKMARK_CREATED, null, 
                type == BookmarkTargetType.ARTICLE ? targetId : null,
                type == BookmarkTargetType.STORY ? targetId : null,
                null, null, null, null);
                
        return new BookmarkStatusResponse(true, bookmark.createdAt());
    }

    public BookmarkStatusResponse status(String userId, BookmarkTargetType type, String targetId) {
        return repository.findByUserIdAndTargetTypeAndTargetId(userId, type, targetId)
                .map(bookmark -> new BookmarkStatusResponse(true, bookmark.createdAt()))
                .orElseGet(() -> new BookmarkStatusResponse(false, null));
    }

    public void delete(String userId, BookmarkTargetType type, String targetId) {
        repository.deleteByUserIdAndTargetTypeAndTargetId(userId, type, targetId);
        analyticsRecorder.recordBestEffort(AnalyticsEventType.BOOKMARK_REMOVED, null, 
                type == BookmarkTargetType.ARTICLE ? targetId : null,
                type == BookmarkTargetType.STORY ? targetId : null,
                null, null, null, null);
    }

    public PagedResponse<BookmarkResponse> list(
            String userId, int page, int size, BookmarkTargetType type, Language displayLanguage) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<UserBookmark> bookmarks = type == null
                ? repository.findByUserId(userId, pageable)
                : repository.findByUserIdAndTargetType(userId, type, pageable);
        Hydration hydration = hydrate(bookmarks.getContent(), displayLanguage);
        List<BookmarkResponse> responses = bookmarks.stream()
                .map(bookmark -> hydration.response(bookmark, displayLanguage)).toList();
        return PagedResponse.from(new PageImpl<>(responses, pageable, bookmarks.getTotalElements()));
    }

    private void requireTarget(BookmarkTargetType type, String targetId) {
        if (type == BookmarkTargetType.ARTICLE) {
            if (!articleRepository.existsById(targetId)) throw new ResourceNotFoundException("Article");
            return;
        }
        Story story = storyRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Story"));
        if (!story.isPubliclyVisible()) throw new ResourceNotFoundException("Story");
    }

    private Hydration hydrate(List<UserBookmark> bookmarks, Language displayLanguage) {
        Set<String> articleIds = bookmarks.stream()
                .filter(bookmark -> bookmark.targetType() == BookmarkTargetType.ARTICLE)
                .map(UserBookmark::targetId).collect(Collectors.toSet());
        Set<String> storyIds = bookmarks.stream()
                .filter(bookmark -> bookmark.targetType() == BookmarkTargetType.STORY)
                .map(UserBookmark::targetId).collect(Collectors.toSet());
        Map<String, Article> articles = map(articleRepository.findAllById(articleIds), Article::id);
        Map<String, Story> stories = map(storyRepository.findAllById(storyIds), Story::id);

        Set<String> representativeIds = stories.values().stream()
                .map(Story::representativeArticleId).collect(Collectors.toSet());
        Map<String, Article> representatives = displayLanguage == null
                ? Map.of() : map(articleRepository.findAllById(representativeIds), Article::id);
        Set<String> sourceIds = articles.values().stream().map(Article::sourceId).collect(Collectors.toSet());
        Map<String, Source> sources = sourceService.findAllByIds(sourceIds).stream()
                .collect(Collectors.toMap(Source::id, Function.identity()));
        return new Hydration(articles, stories, representatives, sources);
    }

    private <T> Map<String, T> map(Iterable<T> values, Function<T, String> id) {
        return StreamSupport.stream(values.spliterator(), false)
                .collect(Collectors.toMap(id, Function.identity()));
    }

    private final class Hydration {
        private final Map<String, Article> articles;
        private final Map<String, Story> stories;
        private final Map<String, Article> representatives;
        private final Map<String, Source> sources;

        private Hydration(Map<String, Article> articles, Map<String, Story> stories,
                Map<String, Article> representatives, Map<String, Source> sources) {
            this.articles = articles;
            this.stories = stories;
            this.representatives = representatives;
            this.sources = sources;
        }

        private BookmarkResponse response(UserBookmark bookmark, Language displayLanguage) {
            if (bookmark.targetType() == BookmarkTargetType.ARTICLE) {
                Article article = articles.get(bookmark.targetId());
                Source source = article == null ? null : sources.get(article.sourceId());
                return new BookmarkResponse(bookmark.id(), bookmark.targetType(), bookmark.targetId(),
                        bookmark.createdAt(), article == null || source == null ? null
                                : articleApiMapper.toResponse(article, source, displayLanguage), null);
            }
            Story story = stories.get(bookmark.targetId());
            LocalizedStoryContentResponse localized = localizeStory(story, displayLanguage);
            return new BookmarkResponse(bookmark.id(), bookmark.targetType(), bookmark.targetId(),
                    bookmark.createdAt(), null, story == null || !story.isPubliclyVisible() ? null
                            : storyApiMapper.toSummary(story, localized));
        }

        private LocalizedStoryContentResponse localizeStory(Story story, Language displayLanguage) {
            if (story == null || displayLanguage == null) return null;
            Article representative = representatives.get(story.representativeArticleId());
            if (representative == null) return new LocalizedStoryContentResponse(
                    displayLanguage, displayLanguage, false, true, story.canonicalTitle());
            var localized = localizationService.localize(representative, displayLanguage);
            return new LocalizedStoryContentResponse(localized.requestedLanguage(),
                    localized.resolvedLanguage(), localized.translated(), localized.fallback(),
                    localized.title());
        }
    }
}
