package lk.srilankannews.article.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.ai.EmbeddingProvider;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleSemanticEmbedding;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.source.api.SourceApiMapper;
import lk.srilankannews.story.StoryEmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArticleSemanticSearchServiceTest {
    @Mock EmbeddingProvider embeddingProvider;
    @Mock ArticleSemanticSearchRepository repository;
    @Mock SourceService sourceService;
    @Mock ArticleApiMapper mapper;
    @Mock lk.srilankannews.analytics.AnalyticsRecorder analyticsRecorder;
    private ArticleSemanticSearchService service;
    private final StoryEmbeddingProperties embeddingProperties =
            new StoryEmbeddingProperties("gemini-embedding-2", 768,
                    "story-semantic-v2", 8000, 0.82, 0.90, 50);

    @BeforeEach
    void setUp() {
        service = new ArticleSemanticSearchService(new TextSearchQueryNormalizer(),
                embeddingProvider, repository, sourceService, mapper, embeddingProperties,
                new SemanticSearchProperties("idx_articles_semantic_vector", 0.65, 200), analyticsRecorder);
    }

    @Test
    void embedsNormalizedUnicodeOnceAppliesFiltersAndBatchHydratesWithoutMutatingArticle() {
        Source source = source();
        Article article = article(source.id());
        Instant embeddedAt = article.semanticEmbedding().embeddedAt();
        List<Double> vector = java.util.Collections.nCopies(768, 0.2);
        when(sourceService.findBySlug(source.slug())).thenReturn(Optional.of(source));
        when(embeddingProvider.embed(any())).thenReturn(vector);
        when(repository.search(any(), any(), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(new SemanticSearchSlice(List.of(article), false));
        when(sourceService.findAllByIds(anyCollection())).thenReturn(List.of(source));
        when(mapper.toResponse(article, source, Language.TA)).thenReturn(response(article, source));

        SemanticSearchResponse result = service.search("  Cafe\u0301\t පුවත්  ", 0, 20,
                source.slug(), ArticleCategory.LOCAL, Language.SI, Language.TA);

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(embeddingProvider, times(1)).embed(input.capture());
        assertThat(input.getValue())
                .isEqualTo("task: sentence similarity | query: Café පුවත්");
        ArgumentCaptor<ArticleSearchFilter> filter =
                ArgumentCaptor.forClass(ArticleSearchFilter.class);
        verify(repository).search(org.mockito.ArgumentMatchers.eq(vector), filter.capture(),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(20));
        assertThat(filter.getValue()).isEqualTo(
                new ArticleSearchFilter(source.id(), ArticleCategory.LOCAL, Language.SI));
        verify(sourceService).findAllByIds(anyCollection());
        verify(mapper).toResponse(article, source, Language.TA);
        assertThat(article.semanticEmbedding().embeddedAt()).isEqualTo(embeddedAt);
        assertThat(result.query()).isEqualTo("Café පුවත්");
        assertThat(result.content()).extracting(ArticleResponse::id).containsExactly(article.id());
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void unknownSourceReturnsEmptyWithoutProviderOrVectorSearch() {
        when(sourceService.findBySlug("unknown")).thenReturn(Optional.empty());

        SemanticSearchResponse result = service.search("cricket", 0, 20, "unknown",
                null, null, null);

        assertThat(result.content()).isEmpty();
        verify(embeddingProvider, never()).embed(any());
        verify(repository, never()).search(any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void rejectsWindowAndMapsProviderOrDimensionFailuresToSafeUnavailableError() {
        assertThatThrownBy(() -> service.search("cricket", 2, 100, null,
                null, null, null)).isInstanceOf(SemanticSearchWindowException.class);

        when(embeddingProvider.embed(any())).thenThrow(new RuntimeException("provider secret"));
        assertThatThrownBy(() -> service.search("cricket", 0, 20, null,
                null, null, null))
                .isInstanceOf(SemanticSearchUnavailableException.class)
                .hasMessage("Semantic search is temporarily unavailable.");

        org.mockito.Mockito.doReturn(List.of(0.1, 0.2))
                .when(embeddingProvider).embed(any());
        assertThatThrownBy(() -> service.search("ක්‍රිකට්", 0, 20, null,
                null, null, null))
                .isInstanceOf(SemanticSearchUnavailableException.class);
    }

    @Test
    void preservesHasMoreAndDoesNotInvokeGenerativeOrPersistenceServices() {
        when(embeddingProvider.embed(any()))
                .thenReturn(java.util.Collections.nCopies(768, 0.2));
        when(repository.search(any(), any(), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(new SemanticSearchSlice(List.of(), true));

        SemanticSearchResponse result = service.search("தமிழ் செய்தி", 0, 10,
                null, null, null, null);

        assertThat(result.hasMore()).isTrue();
        assertThat(result.first()).isTrue();
    }

    private Article article(String sourceId) {
        Instant now = Instant.parse("2026-09-02T10:00:00Z");
        ArticleSemanticEmbedding embedding = new ArticleSemanticEmbedding(
                java.util.Collections.nCopies(768, 0.1), "gemini-embedding-2", 768,
                "story-semantic-v2", "private-hash", now.minusSeconds(60));
        return new Article("article-1", sourceId, "Public title", "https://example.com/1",
                "https://example.com/1", Language.SI, List.of(), now, now,
                ArticleCategory.LOCAL, "Private extracted body", "content-hash", null,
                embedding, ProcessingStatus.COMPLETED, null, now, now);
    }

    private Source source() {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        return new Source("source-1", "Hiru News", "hiru-news-sinhala",
                "https://example.com", Language.SI, IngestionType.HTML, true, now, now);
    }

    private ArticleResponse response(Article article, Source source) {
        return new ArticleResponse(article.id(), article.title(), article.originalUrl(),
                article.originalLanguage(), article.authors(), article.publishedAt(),
                article.discoveredAt(), article.category(), new SourceApiMapper().toSummary(source));
    }
}
