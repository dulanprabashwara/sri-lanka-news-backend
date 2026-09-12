package lk.srilankannews.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class PendingArticleEventRecoveryTest {

    private ArticleService articleService;
    private ArticleDiscoveredNotifier notifier;
    private Clock clock;
    private Instant now;

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        notifier = mock(ArticleDiscoveredNotifier.class);
        now = Instant.parse("2026-09-12T12:00:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);
    }

    @Test
    void recoverStalePendingArticlesDispatchesOnlyStalePendingArticles() {
        PendingArticleRecoveryProperties properties = new PendingArticleRecoveryProperties(
                true, Duration.ofMinutes(5), Duration.ofMinutes(5), 50);
        PendingArticleEventRecovery recovery = new PendingArticleEventRecovery(
                articleService, notifier, properties, clock);

        Instant threshold = now.minus(Duration.ofMinutes(5));
        Article article1 = sampleArticle("art-1", "source-1");
        Article article2 = sampleArticle("art-2", "source-2");

        when(articleService.findStalePendingArticles(eq(threshold), any(PageRequest.class)))
                .thenReturn(List.of(article1, article2));

        int recovered = recovery.recoverStalePendingArticles();

        assertThat(recovered).isEqualTo(2);
        verify(notifier).notifyDiscovered(article1);
        verify(notifier).notifyDiscovered(article2);
    }

    @Test
    void recoverStalePendingArticlesDoesNothingWhenNoStaleArticles() {
        PendingArticleRecoveryProperties properties = new PendingArticleRecoveryProperties(
                true, Duration.ofMinutes(5), Duration.ofMinutes(5), 50);
        PendingArticleEventRecovery recovery = new PendingArticleEventRecovery(
                articleService, notifier, properties, clock);

        Instant threshold = now.minus(Duration.ofMinutes(5));
        when(articleService.findStalePendingArticles(eq(threshold), any(PageRequest.class)))
                .thenReturn(List.of());

        int recovered = recovery.recoverStalePendingArticles();

        assertThat(recovered).isZero();
        verify(notifier, never()).notifyDiscovered(any());
    }

    @Test
    void recoverScheduledHonorsDisabledProperty() {
        PendingArticleRecoveryProperties properties = new PendingArticleRecoveryProperties(
                false, Duration.ofMinutes(5), Duration.ofMinutes(5), 50);
        PendingArticleEventRecovery recovery = new PendingArticleEventRecovery(
                articleService, notifier, properties, clock);

        int recovered = recovery.recoverScheduled();

        assertThat(recovered).isZero();
        verify(articleService, never()).findStalePendingArticles(any(), any());
        verify(notifier, never()).notifyDiscovered(any());
    }

    @Test
    void recoverStalePendingArticlesContinuesOnError() {
        PendingArticleRecoveryProperties properties = new PendingArticleRecoveryProperties(
                true, Duration.ofMinutes(5), Duration.ofMinutes(5), 50);
        PendingArticleEventRecovery recovery = new PendingArticleEventRecovery(
                articleService, notifier, properties, clock);

        Instant threshold = now.minus(Duration.ofMinutes(5));
        Article article1 = sampleArticle("art-1", "source-1");
        Article article2 = sampleArticle("art-2", "source-2");

        when(articleService.findStalePendingArticles(eq(threshold), any(PageRequest.class)))
                .thenReturn(List.of(article1, article2));
        doThrow(new RuntimeException("Dispatch failed")).when(notifier).notifyDiscovered(article1);

        int recovered = recovery.recoverStalePendingArticles();

        assertThat(recovered).isEqualTo(1);
        verify(notifier).notifyDiscovered(article1);
        verify(notifier).notifyDiscovered(article2);
    }

    @Test
    void runApplicationRunnerInvokesRecovery() {
        PendingArticleRecoveryProperties properties = new PendingArticleRecoveryProperties(
                true, Duration.ofMinutes(5), Duration.ofMinutes(5), 50);
        PendingArticleEventRecovery recovery = new PendingArticleEventRecovery(
                articleService, notifier, properties, clock);

        Instant threshold = now.minus(Duration.ofMinutes(5));
        when(articleService.findStalePendingArticles(eq(threshold), any(PageRequest.class)))
                .thenReturn(List.of());

        recovery.run(null);

        verify(articleService).findStalePendingArticles(eq(threshold), any(PageRequest.class));
    }

    private Article sampleArticle(String id, String sourceId) {
        return new Article(
                id,
                sourceId,
                "Title " + id,
                "https://example.com/" + id,
                "https://example.com/" + id,
                Language.EN,
                List.of(),
                now.minus(Duration.ofHours(1)),
                now.minus(Duration.ofMinutes(10)),
                ArticleCategory.OTHER,
                "Extracted content",
                "Summary",
                "hash-" + id,
                null,
                null,
                java.util.Map.of(),
                null,
                ProcessingStatus.PENDING,
                null,
                now.minus(Duration.ofMinutes(10)),
                now.minus(Duration.ofMinutes(10)));
    }
}
