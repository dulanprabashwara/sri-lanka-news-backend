package lk.srilankannews.article.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.source.api.SourceApiMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ArticleTextSearchServiceTest {
    @Mock ArticleTextSearchRepository repository;
    @Mock SourceService sourceService;
    @Mock ArticleApiMapper mapper;
    private ArticleTextSearchService service;

    @BeforeEach
    void setUp() {
        service = new ArticleTextSearchService(new TextSearchQueryNormalizer(), repository,
                sourceService, mapper);
    }

    @Test
    void normalizesQueryAppliesCombinedFiltersAndBatchHydratesSources() {
        Source source = source();
        Article article = article("article-1", source.id());
        when(sourceService.findBySlug(source.slug())).thenReturn(Optional.of(source));
        when(repository.search(any(), any(), any())).thenAnswer(invocation ->
                new PageImpl<>(List.of(article), invocation.getArgument(2), 1));
        when(sourceService.findAllByIds(anyCollection())).thenReturn(List.of(source));
        when(mapper.toResponse(article, source, Language.TA)).thenReturn(response(article, source));

        TextSearchResponse result = service.search("  Café\t news  ", 0, 20, source.slug(),
                ArticleCategory.LOCAL, Language.EN, Language.TA);

        ArgumentCaptor<ArticleSearchFilter> filter = ArgumentCaptor.forClass(ArticleSearchFilter.class);
        verify(repository).search(eq("Café news"), filter.capture(), any(Pageable.class));
        assertThat(filter.getValue()).isEqualTo(
                new ArticleSearchFilter(source.id(), ArticleCategory.LOCAL, Language.EN));
        verify(sourceService).findAllByIds(anyCollection());
        verify(mapper).toResponse(article, source, Language.TA);
        assertThat(result.query()).isEqualTo("Café news");
        assertThat(result.content()).extracting(ArticleResponse::id).containsExactly("article-1");
    }

    @Test
    void unknownSourceReturnsExistingConventionEmptyPageWithoutMongoSearch() {
        when(sourceService.findBySlug("unknown")).thenReturn(Optional.empty());

        TextSearchResponse result = service.search("cricket", 3, 10, "unknown",
                null, null, null);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(3);
        assertThat(result.query()).isEqualTo("cricket");
        verify(repository, never()).search(any(), any(), any());
        verify(sourceService, never()).findAllByIds(anyCollection());
    }

    private Article article(String id, String sourceId) {
        Instant now = Instant.parse("2026-09-02T10:00:00Z");
        return new Article(id, sourceId, "Public title", "https://example.com/" + id,
                "https://example.com/" + id, Language.EN, List.of(), now, now,
                ArticleCategory.LOCAL, "Private extracted body", now, now);
    }

    private Source source() {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        return new Source("source-1", "NewsFirst", "newsfirst", "https://example.com",
                Language.EN, IngestionType.RSS, true, now, now);
    }

    private ArticleResponse response(Article article, Source source) {
        return new ArticleResponse(article.id(), article.title(), article.originalUrl(),
                article.originalLanguage(), article.authors(), article.publishedAt(),
                article.discoveredAt(), article.category(), new SourceApiMapper().toSummary(source));
    }
}
