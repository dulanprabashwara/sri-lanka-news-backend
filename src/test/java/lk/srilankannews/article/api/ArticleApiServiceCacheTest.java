package lk.srilankannews.article.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleFilter;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.cache.ArticleFeedCache;
import lk.srilankannews.article.cache.ArticleFeedQuery;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.source.api.SourceApiMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ArticleApiServiceCacheTest {
    @Mock
    private ArticleService articleService;
    @Mock
    private SourceService sourceService;

    private InMemoryFeedCache feedCache;
    private ArticleApiService service;

    @BeforeEach
    void setUp() {
        feedCache = new InMemoryFeedCache();
        service = new ArticleApiService(
                articleService,
                sourceService,
                new ArticleApiMapper(new SourceApiMapper()),
                feedCache);
    }

    @Test
    void firstRequestMissesAndSecondIdenticalRequestAvoidsMongoQuery() {
        stubFeed();

        PagedResponse<ArticleResponse> first =
                service.list(0, 20, null, null, null, Sort.Direction.DESC);
        PagedResponse<ArticleResponse> second =
                service.list(0, 20, null, null, null, Sort.Direction.DESC);

        assertThat(second).isEqualTo(first);
        assertThat(feedCache.entries).hasSize(1);
        verify(articleService, times(1))
                .findAll(any(ArticleFilter.class), any(Pageable.class));
        verify(sourceService, times(1)).findAllByIds(anyCollection());
    }

    @Test
    void aDifferentPageUsesASeparateEntryAndMongoQuery() {
        stubFeed();

        service.list(0, 20, null, null, null, Sort.Direction.DESC);
        service.list(1, 20, null, null, null, Sort.Direction.DESC);

        assertThat(feedCache.entries).hasSize(2);
        verify(articleService, times(2))
                .findAll(any(ArticleFilter.class), any(Pageable.class));
    }

    private void stubFeed() {
        Source source = source();
        when(articleService.findAll(any(ArticleFilter.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(article()), invocation.getArgument(1), 1));
        when(sourceService.findAllByIds(anyCollection())).thenReturn(List.of(source));
    }

    private Article article() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        return new Article(
                "article-1",
                "source-1",
                "Public headline",
                "https://publisher.example/article-1",
                "https://publisher.example/article-1",
                Language.EN,
                List.of("Reporter"),
                now,
                now,
                ArticleCategory.LOCAL,
                "Private extracted content",
                now,
                now);
    }

    private Source source() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        return new Source(
                "source-1",
                "Publisher",
                "publisher",
                "https://publisher.example",
                Language.EN,
                IngestionType.RSS,
                true,
                now,
                now);
    }

    private static final class InMemoryFeedCache implements ArticleFeedCache {
        private final Map<String, PagedResponse<ArticleResponse>> entries = new HashMap<>();
        private String generation = "0";

        @Override
        public Lookup get(ArticleFeedQuery query) {
            PagedResponse<ArticleResponse> response =
                    entries.get(query.cacheKey(generation));
            return response == null
                    ? Lookup.miss(generation)
                    : Lookup.hit(response, generation);
        }

        @Override
        public void put(
                ArticleFeedQuery query,
                String generation,
                PagedResponse<ArticleResponse> response) {
            entries.put(query.cacheKey(generation), response);
        }

        @Override
        public void invalidate() {
            generation = Integer.toString(Integer.parseInt(generation) + 1);
        }
    }
}
