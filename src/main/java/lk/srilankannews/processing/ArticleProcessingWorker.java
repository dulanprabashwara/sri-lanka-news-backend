package lk.srilankannews.processing;

import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.processing.enrichment.ArticleEnrichmentService;
import lk.srilankannews.story.ArticleEmbeddingService;
import lk.srilankannews.story.StoryClusteringService;
import lk.srilankannews.translation.ArticleTranslationService;
import lk.srilankannews.notifications.NotificationEventOutboxService;
import org.springframework.stereotype.Service;

@Service
public class ArticleProcessingWorker {
    private final ArticleService articleService;
    private final ArticleEnrichmentService enrichmentService;
    private final ArticleEmbeddingService embeddingService;
    private final StoryClusteringService clusteringService;
    private final ArticleTranslationService translationService;
    private final NotificationEventOutboxService outboxService;

    public ArticleProcessingWorker(
            ArticleService articleService,
            ArticleEnrichmentService enrichmentService,
            ArticleEmbeddingService embeddingService,
            StoryClusteringService clusteringService,
            ArticleTranslationService translationService,
            NotificationEventOutboxService outboxService) {
        this.articleService = articleService;
        this.enrichmentService = enrichmentService;
        this.embeddingService = embeddingService;
        this.clusteringService = clusteringService;
        this.translationService = translationService;
        this.outboxService = outboxService;
    }

    public void process(ArticleDiscoveredEvent event) {
        translate(event);
        processAfterTranslation(event);
    }

    public void translate(ArticleDiscoveredEvent event) {
        Article article = articleService.findById(event.articleId())
                .orElseThrow(() -> new IllegalStateException("Article does not exist"));
        validateEvent(event, article);
        ensureTranslations(article.id());
    }

    public void processAfterTranslation(ArticleDiscoveredEvent event) {
        Article article = articleService.findById(event.articleId())
                .orElseThrow(() -> new IllegalStateException("Article does not exist"));
        validateEvent(event, article);
        ArticleEnrichmentService.Outcome enrichment = enrichmentService.attempt(article.id());
        if (enrichment.enrichmentAvailable()) {
            ensureTranslations(article.id());
        }
        embeddingService.ensureEmbedding(article.id());
        String storyId = clusteringService.cluster(article.id());
        
        if (outboxService != null && storyId != null) {
            outboxService.dispatch(article.id(), storyId, article.sourceId(), article.id());
        }
    }

    private void ensureTranslations(String articleId) {
        if (translationService == null) {
            return;
        }
        translationService.ensureTranslations(articleId);
    }

    public void markRetrying(String articleId) {
        articleService.updateProcessingStatus(articleId, ProcessingStatus.RETRYING);
    }

    public void markFailed(String articleId) {
        articleService.updateProcessingStatus(articleId, ProcessingStatus.FAILED);
    }

    private void validateEvent(ArticleDiscoveredEvent event, Article article) {
        if (!article.sourceId().equals(event.sourceId())
                || event.eventVersion() != ArticleDiscoveredEvent.CURRENT_VERSION) {
            throw new IllegalArgumentException("Article event metadata is invalid");
        }
    }

}
