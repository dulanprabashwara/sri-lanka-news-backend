package lk.srilankannews.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.processing.ArticleDiscoveredNotifier;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    @Mock AdminMongoOperations operations;
    @Mock SourceRepository sourceRepository;
    @Mock ArticleRepository articleRepository;
    @Mock lk.srilankannews.ingestion.run.IngestionRunRepository ingestionRunRepository;
    @Mock ArticleDiscoveredNotifier notifier;
    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(operations, sourceRepository, articleRepository,
                ingestionRunRepository, notifier, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void overviewReturnsStatusCountsAndBoundedSafeFailuresWithOneSourceLookup() {
        Source source = source();
        Article failed = article("article-1", ProcessingStatus.FAILED);
        when(operations.storyCount()).thenReturn(7L);
        when(operations.articleCount()).thenReturn(20L);
        when(operations.articleCount(ProcessingStatus.PENDING)).thenReturn(1L);
        when(operations.articleCount(ProcessingStatus.PROCESSING)).thenReturn(2L);
        when(operations.articleCount(ProcessingStatus.COMPLETED)).thenReturn(14L);
        when(operations.articleCount(ProcessingStatus.RETRYING)).thenReturn(1L);
        when(operations.articleCount(ProcessingStatus.FAILED)).thenReturn(2L);
        when(operations.findArticles(ProcessingStatus.FAILED, null, 10)).thenReturn(List.of(failed));
        when(sourceRepository.findAll()).thenReturn(List.of(source));
        when(sourceRepository.findAllById(Set.of("source-1"))).thenReturn(List.of(source));

        AdminOverviewResponse result = service.overview();

        assertThat(result.sources().total()).isEqualTo(1);
        assertThat(result.articles().failed()).isEqualTo(2);
        assertThat(result.stories().total()).isEqualTo(7);
        assertThat(result.recentFailures()).hasSize(1);
        assertThat(AdminArticleResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("extractedContent", "semanticEmbedding", "contentHash",
                        "prompt", "error", "failureDetail");
        verify(sourceRepository).findAllById(Set.of("source-1"));
    }

    @Test
    void sourceListUsesOneBatchedCountAggregation() {
        Source source = source();
        when(sourceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(source));
        when(operations.articleCountsBySource()).thenReturn(Map.of("source-1", 12L));

        assertThat(service.sources()).singleElement()
                .satisfies(item -> assertThat(item.articleCount()).isEqualTo(12));
    }

    @Test
    void articleFiltersResolveSlugOnceAndUseBoundedQuery() {
        Source source = source();
        when(sourceRepository.findBySlug("newsfirst")).thenReturn(java.util.Optional.of(source));
        when(operations.findArticles(ProcessingStatus.FAILED, "source-1", 25))
                .thenReturn(List.of(article("article-1", ProcessingStatus.FAILED)));
        when(sourceRepository.findAllById(Set.of("source-1"))).thenReturn(List.of(source));

        assertThat(service.articles(ProcessingStatus.FAILED, "newsfirst", 25)).hasSize(1);
        verify(operations).findArticles(ProcessingStatus.FAILED, "source-1", 25);
    }

    @Test
    void retryAtomicallyClaimsFailedArticleAndReusesNotifier() {
        Article retrying = article("article-1", ProcessingStatus.RETRYING);
        when(operations.claimFailedForRetry("article-1", NOW)).thenReturn(retrying);
        when(sourceRepository.findAllById(Set.of("source-1"))).thenReturn(List.of(source()));

        AdminArticleResponse result = service.retry("article-1");

        assertThat(result.processingStatus()).isEqualTo(ProcessingStatus.RETRYING);
        verify(notifier).notifyDiscovered(retrying);
    }

    @Test
    void repeatedOrNonFailedRetryIsRejected() {
        when(operations.claimFailedForRetry("article-1", NOW)).thenReturn(null);
        when(articleRepository.existsById("article-1")).thenReturn(true);

        assertThatThrownBy(() -> service.retry("article-1"))
                .isInstanceOf(AdminRetryNotAllowedException.class);
    }

    private Source source() {
        return new Source("source-1", "NewsFirst", "newsfirst", "https://newsfirst.lk",
                Language.EN, IngestionType.HTML, true, NOW, NOW);
    }

    private Article article(String id, ProcessingStatus status) {
        return new Article(id, "source-1", "Safe operational title",
                "https://example.com/article", "https://example.com/article", Language.EN,
                List.of(), NOW.minusSeconds(60), NOW, null, "private body", "private-hash",
                status, NOW, NOW);
    }
}
