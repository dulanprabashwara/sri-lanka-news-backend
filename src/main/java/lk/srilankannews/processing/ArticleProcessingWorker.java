package lk.srilankannews.processing;

import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import org.springframework.stereotype.Service;

@Service
public class ArticleProcessingWorker {
    private final ArticleService articleService;

    public ArticleProcessingWorker(ArticleService articleService) {
        this.articleService = articleService;
    }

    public void process(ArticleDiscoveredEvent event) {
        Article article = articleService.findById(event.articleId())
                .orElseThrow(() -> new IllegalStateException("Article does not exist"));
        if (article.processingStatus() == ProcessingStatus.COMPLETED) {
            return;
        }
        if (!article.sourceId().equals(event.sourceId())
                || event.eventVersion() != ArticleDiscoveredEvent.CURRENT_VERSION) {
            throw new IllegalArgumentException("Article event metadata is invalid");
        }
        articleService.updateProcessingStatus(article.id(), ProcessingStatus.PROCESSING)
                .orElseThrow(() -> new IllegalStateException("Article disappeared during processing"));

        // Phase 9 proof action only; no enrichment or external AI call occurs.
        articleService.updateProcessingStatus(article.id(), ProcessingStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("Article disappeared during processing"));
    }

    public void markRetrying(String articleId) {
        articleService.updateProcessingStatus(articleId, ProcessingStatus.RETRYING);
    }

    public void markFailed(String articleId) {
        articleService.updateProcessingStatus(articleId, ProcessingStatus.FAILED);
    }
}
