package lk.srilankannews.article.search;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.srilankannews.ai.EmbeddingProvider;
import lk.srilankannews.ai.SemanticSimilarityEmbeddingInput;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.StoryEmbeddingProperties;
import org.springframework.stereotype.Service;
import lk.srilankannews.analytics.AnalyticsRecorder;
import lk.srilankannews.analytics.AnalyticsEventType;

@Service
public class ArticleSemanticSearchService {
    private final TextSearchQueryNormalizer normalizer;
    private final EmbeddingProvider embeddingProvider;
    private final ArticleSemanticSearchRepository repository;
    private final SourceService sourceService;
    private final ArticleApiMapper mapper;
    private final StoryEmbeddingProperties embeddingProperties;
    private final SemanticSearchProperties searchProperties;
    private final AnalyticsRecorder analyticsRecorder;

    public ArticleSemanticSearchService(TextSearchQueryNormalizer normalizer,
            EmbeddingProvider embeddingProvider,
            ArticleSemanticSearchRepository repository,
            SourceService sourceService,
            ArticleApiMapper mapper,
            StoryEmbeddingProperties embeddingProperties,
            SemanticSearchProperties searchProperties,
            AnalyticsRecorder analyticsRecorder) {
        this.normalizer = normalizer;
        this.embeddingProvider = embeddingProvider;
        this.repository = repository;
        this.sourceService = sourceService;
        this.mapper = mapper;
        this.embeddingProperties = embeddingProperties;
        this.searchProperties = searchProperties;
        this.analyticsRecorder = analyticsRecorder;
    }

    public SemanticSearchResponse search(String query, int page, int size, String sourceSlug,
            ArticleCategory category, Language language, Language displayLanguage) {
        String normalized = normalizer.normalize(query);
        validateWindow(page, size);
        Optional<String> sourceId = resolveSourceId(sourceSlug);
        if (sourceSlug != null && sourceId.isEmpty()) {
            return SemanticSearchResponse.empty(normalized, page, size);
        }

        List<Double> queryVector;
        try {
            queryVector = List.copyOf(embeddingProvider.embed(
                    SemanticSimilarityEmbeddingInput.format(normalized)));
            validateVector(queryVector);
        } catch (RuntimeException exception) {
            throw new SemanticSearchUnavailableException(exception);
        }

        SemanticSearchSlice result;
        try {
            result = repository.search(queryVector,
                    new ArticleSearchFilter(sourceId.orElse(null), category, language), page, size);
        } catch (RuntimeException exception) {
            throw new SemanticSearchUnavailableException(exception);
        }

        Set<String> sourceIds = result.content().stream()
                .map(Article::sourceId)
                .collect(Collectors.toSet());
        Map<String, Source> sources = sourceService.findAllByIds(sourceIds).stream()
                .collect(Collectors.toMap(Source::id, Function.identity()));
        List<ArticleResponse> responses = result.content().stream()
                .map(article -> mapper.toResponse(
                        article, requireSource(article, sources), displayLanguage))
                .toList();
                
        if (page == 0) {
            analyticsRecorder.recordBestEffort(AnalyticsEventType.SEARCH_EXECUTED, null, null, null, sourceSlug, responses.size(), "SEMANTIC", null);
        }
        
        return new SemanticSearchResponse(normalized, responses, page, size,
                result.hasMore(), page == 0);
    }

    private void validateWindow(int page, int size) {
        long endExclusive = (long) page * size + size;
        if (endExclusive > searchProperties.maxWindow()) {
            throw new SemanticSearchWindowException(searchProperties.maxWindow());
        }
    }

    private void validateVector(List<Double> values) {
        if (values.size() != embeddingProperties.dimensions()
                || values.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalStateException("Query embedding is incompatible with Article embeddings.");
        }
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
