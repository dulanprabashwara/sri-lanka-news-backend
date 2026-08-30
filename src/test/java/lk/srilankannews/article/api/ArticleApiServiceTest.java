package lk.srilankannews.article.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleFilter;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
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
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ArticleApiServiceTest {

    @Mock
    private ArticleService articleService;

    @Mock
    private SourceService sourceService;

    private ArticleApiService articleApiService;

    @BeforeEach
    void setUp() {
        ArticleApiMapper mapper = new ArticleApiMapper(new SourceApiMapper());
        articleApiService = new ArticleApiService(articleService, sourceService, mapper);
    }

    @Test
    void appliesFiltersAndBatchesSourceAttributionForThePage() {
        Source sourceOne = source("source-1", "daily-mirror", "Daily Mirror");
        Source sourceTwo = source("source-2", "ada-derana", "Ada Derana");
        Article articleOne = article("article-1", sourceOne.id(), Instant.parse("2026-08-30T09:00:00Z"));
        Article articleTwo = article("article-2", sourceTwo.id(), Instant.parse("2026-08-30T08:00:00Z"));
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(sourceOne));
        when(articleService.findAll(any(ArticleFilter.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(articleOne, articleTwo), invocation.getArgument(1), 2));
        when(sourceService.findAllByIds(anyCollection())).thenReturn(List.of(sourceOne, sourceTwo));

        PagedResponse<ArticleResponse> response = articleApiService.list(
                1, 10, "daily-mirror", ArticleCategory.POLITICS, Language.EN, Sort.Direction.DESC);

        ArgumentCaptor<ArticleFilter> filterCaptor = ArgumentCaptor.forClass(ArticleFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(articleService).findAll(filterCaptor.capture(), pageableCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(
                new ArticleFilter("source-1", ArticleCategory.POLITICS, Language.EN));
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("publishedAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(response.content())
                .extracting(item -> item.source().slug())
                .containsExactly("daily-mirror", "ada-derana");
        verify(sourceService).findAllByIds(anyCollection());
    }

    @Test
    void returnsEmptyPageWithoutArticleQueryForUnknownSourceFilter() {
        when(sourceService.findBySlug("unknown-source")).thenReturn(Optional.empty());

        PagedResponse<ArticleResponse> response = articleApiService.list(
                3, 5, "unknown-source", null, null, Sort.Direction.DESC);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(3);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isZero();
        verify(articleService, never()).findAll(any(), any());
        verify(sourceService, never()).findAllByIds(anyCollection());
    }

    @Test
    void mapsArticleDetailWithSourceAttribution() {
        Article article = article("507f1f77bcf86cd799439011", "source-1", Instant.parse("2026-08-30T09:00:00Z"));
        Source source = source("source-1", "daily-mirror", "Daily Mirror");
        when(articleService.findById(article.id())).thenReturn(Optional.of(article));
        when(sourceService.findById(source.id())).thenReturn(Optional.of(source));

        ArticleResponse response = articleApiService.detail(article.id());

        assertThat(response.id()).isEqualTo(article.id());
        assertThat(response.source().slug()).isEqualTo("daily-mirror");
    }

    @Test
    void rejectsUnknownArticleDetail() {
        when(articleService.findById("507f1f77bcf86cd799439011")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleApiService.detail("507f1f77bcf86cd799439011"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Article was not found.");
    }

    private Article article(String id, String sourceId, Instant publishedAt) {
        return new Article(
                id,
                sourceId,
                "Sri Lanka news headline",
                "https://example.com/article/" + id,
                "https://example.com/article/" + id,
                Language.EN,
                List.of("Reporter One"),
                publishedAt,
                publishedAt.plusSeconds(60),
                ArticleCategory.POLITICS,
                publishedAt.plusSeconds(60),
                publishedAt.plusSeconds(60));
    }

    private Source source(String id, String slug, String name) {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        return new Source(
                id,
                name,
                slug,
                "https://example.com/" + slug,
                Language.EN,
                IngestionType.RSS,
                true,
                now,
                now);
    }
}
