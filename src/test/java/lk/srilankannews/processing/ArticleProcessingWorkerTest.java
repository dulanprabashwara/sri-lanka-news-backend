package lk.srilankannews.processing;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;

class ArticleProcessingWorkerTest {

    @Test
    void performsDeterministicStatusTransition() {
        ArticleService articleService = org.mockito.Mockito.mock(ArticleService.class);
        when(articleService.findById("article-1")).thenReturn(Optional.of(article(ProcessingStatus.PENDING)));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.PROCESSING))
                .thenReturn(Optional.of(article(ProcessingStatus.PROCESSING)));
        when(articleService.updateProcessingStatus("article-1", ProcessingStatus.COMPLETED))
                .thenReturn(Optional.of(article(ProcessingStatus.COMPLETED)));

        new ArticleProcessingWorker(articleService).process(event());

        verify(articleService).updateProcessingStatus("article-1", ProcessingStatus.PROCESSING);
        verify(articleService).updateProcessingStatus("article-1", ProcessingStatus.COMPLETED);
    }

    @Test
    void treatsAlreadyCompletedDuplicateDeliveryAsNoOp() {
        ArticleService articleService = org.mockito.Mockito.mock(ArticleService.class);
        when(articleService.findById("article-1"))
                .thenReturn(Optional.of(article(ProcessingStatus.COMPLETED)));

        new ArticleProcessingWorker(articleService).process(event());

        verify(articleService, never())
                .updateProcessingStatus("article-1", ProcessingStatus.PROCESSING);
    }

    private ArticleDiscoveredEvent event() {
        return new ArticleDiscoveredEvent(
                "event-1", "article-1", "source-1",
                Instant.parse("2026-08-30T10:00:00Z"), 1, 1);
    }

    private Article article(ProcessingStatus status) {
        Instant now = Instant.parse("2026-08-30T10:00:00Z");
        return new Article(
                "article-1", "source-1", "Headline", "https://example.com/1",
                "https://example.com/1", Language.EN, List.of(), now, now,
                ArticleCategory.LOCAL, "Internal content", "hash", status, now, now);
    }
}
