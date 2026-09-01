package lk.srilankannews.article.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleFilter;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.cache.ArticleFeedCache;
import lk.srilankannews.article.cache.ArticleFeedQuery;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ArticleApiService {

    private final ArticleService articleService;
    private final SourceService sourceService;
    private final ArticleApiMapper articleApiMapper;
    private final ArticleFeedCache feedCache;

    public ArticleApiService(
            ArticleService articleService,
            SourceService sourceService,
            ArticleApiMapper articleApiMapper,
            ArticleFeedCache feedCache) {
        this.articleService = articleService;
        this.sourceService = sourceService;
        this.articleApiMapper = articleApiMapper;
        this.feedCache = feedCache;
    }

    public PagedResponse<ArticleResponse> list(
            int page,
            int size,
            String sourceSlug,
            ArticleCategory category,
            Language language,
            Sort.Direction direction) {
        return list(page, size, sourceSlug, category, language, null, direction);
    }

    public PagedResponse<ArticleResponse> list(
            int page,
            int size,
            String sourceSlug,
            ArticleCategory category,
            Language language,
            Language displayLanguage,
            Sort.Direction direction) {
        ArticleFeedQuery query =
                new ArticleFeedQuery(
                        page, size, sourceSlug, category, language, displayLanguage, direction);
        ArticleFeedCache.Lookup lookup = feedCache.get(query);
        if (lookup.response().isPresent()) {
            return lookup.response().orElseThrow();
        }

        PagedResponse<ArticleResponse> response =
                loadFeed(page, size, sourceSlug, category, language, displayLanguage, direction);
        feedCache.put(query, lookup.generation(), response);
        return response;
    }

    public ArticleResponse detail(String id) {
        return detail(id, null);
    }

    public ArticleResponse detail(String id, Language displayLanguage) {
        Article article = articleService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article"));
        Source source = sourceService.findById(article.sourceId())
                .orElseThrow(() -> new IllegalStateException("Article source attribution is missing."));
        return articleApiMapper.toResponse(article, source, displayLanguage);
    }

    private PagedResponse<ArticleResponse> loadFeed(
            int page,
            int size,
            String sourceSlug,
            ArticleCategory category,
            Language language,
            Language displayLanguage,
            Sort.Direction direction) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, "publishedAt").and(Sort.by(direction, "id")));

        Optional<String> sourceId = resolveSourceId(sourceSlug);
        if (sourceSlug != null && sourceId.isEmpty()) {
            return PagedResponse.from(Page.empty(pageable));
        }

        ArticleFilter filter = new ArticleFilter(sourceId.orElse(null), category, language);
        Page<Article> articles = articleService.findAll(filter, pageable);
        Map<String, Source> sourcesById = sourcesById(articles);

        return PagedResponse.from(articles.map(article -> articleApiMapper.toResponse(
                article,
                sourceFor(article, sourcesById), displayLanguage)));
    }

    private Optional<String> resolveSourceId(String sourceSlug) {
        if (sourceSlug == null) {
            return Optional.empty();
        }
        return sourceService.findBySlug(sourceSlug).map(Source::id);
    }

    private Map<String, Source> sourcesById(Page<Article> articles) {
        Set<String> sourceIds = articles.stream()
                .map(Article::sourceId)
                .collect(Collectors.toSet());
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
