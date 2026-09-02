package lk.srilankannews.article.search;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ArticleTextSearchService {
    private final TextSearchQueryNormalizer normalizer;
    private final ArticleTextSearchRepository repository;
    private final SourceService sourceService;
    private final ArticleApiMapper mapper;

    public ArticleTextSearchService(TextSearchQueryNormalizer normalizer,
            ArticleTextSearchRepository repository, SourceService sourceService,
            ArticleApiMapper mapper) {
        this.normalizer = normalizer;
        this.repository = repository;
        this.sourceService = sourceService;
        this.mapper = mapper;
    }

    public TextSearchResponse search(String query, int page, int size, String sourceSlug,
            ArticleCategory category, Language language, Language displayLanguage) {
        String normalized = normalizer.normalize(query);
        PageRequest pageable = PageRequest.of(page, size);
        Optional<String> sourceId = resolveSourceId(sourceSlug);
        if (sourceSlug != null && sourceId.isEmpty()) {
            return TextSearchResponse.from(normalized, Page.empty(pageable));
        }
        Page<Article> articles = repository.search(normalized,
                new ArticleSearchFilter(sourceId.orElse(null), category, language), pageable);
        Set<String> sourceIds = articles.stream().map(Article::sourceId).collect(Collectors.toSet());
        Map<String, Source> sources = sourceService.findAllByIds(sourceIds).stream()
                .collect(Collectors.toMap(Source::id, Function.identity()));
        Page<ArticleResponse> responses = articles.map(article -> mapper.toResponse(
                article, requireSource(article, sources), displayLanguage));
        return TextSearchResponse.from(normalized, responses);
    }

    private Optional<String> resolveSourceId(String slug) {
        return slug == null ? Optional.empty() : sourceService.findBySlug(slug).map(Source::id);
    }

    private Source requireSource(Article article, Map<String, Source> sources) {
        Source source = sources.get(article.sourceId());
        if (source == null) {
            throw new IllegalStateException("Article source attribution is missing.");
        }
        return source;
    }
}
