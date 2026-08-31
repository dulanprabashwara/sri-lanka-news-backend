package lk.srilankannews.processing;

import java.time.Clock;
import lk.srilankannews.ai.AiEntity;
import lk.srilankannews.ai.AiInput;
import lk.srilankannews.ai.AiInputPolicy;
import lk.srilankannews.ai.AiOutputValidator;
import lk.srilankannews.ai.AiProvider;
import lk.srilankannews.ai.AiResult;
import lk.srilankannews.ai.GeminiProperties;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleEntity;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.article.cache.ArticleFeedCache;
import lk.srilankannews.story.ArticleEmbeddingService;
import lk.srilankannews.story.StoryClusteringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ArticleProcessingWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleProcessingWorker.class);

    private final ArticleService articleService;
    private final AiProvider aiProvider;
    private final AiInputPolicy inputPolicy;
    private final AiOutputValidator outputValidator;
    private final GeminiProperties properties;
    private final ArticleFeedCache feedCache;
    private final ArticleEmbeddingService embeddingService;
    private final StoryClusteringService clusteringService;
    private final Clock clock;

    public ArticleProcessingWorker(
            ArticleService articleService,
            AiProvider aiProvider,
            AiInputPolicy inputPolicy,
            AiOutputValidator outputValidator,
            GeminiProperties properties,
            ArticleFeedCache feedCache,
            ArticleEmbeddingService embeddingService,
            StoryClusteringService clusteringService,
            Clock clock) {
        this.articleService = articleService;
        this.aiProvider = aiProvider;
        this.inputPolicy = inputPolicy;
        this.outputValidator = outputValidator;
        this.properties = properties;
        this.feedCache = feedCache;
        this.embeddingService = embeddingService;
        this.clusteringService = clusteringService;
        this.clock = clock;
    }

    public void process(ArticleDiscoveredEvent event) {
        Article article = articleService.findById(event.articleId())
                .orElseThrow(() -> new IllegalStateException("Article does not exist"));
        validateEvent(event, article);
        if (article.aiEnrichment() == null
                || !article.aiEnrichment().matches(
                        properties.model(), properties.promptVersion())) {
            article = enrich(article);
        }
        embeddingService.ensureEmbedding(article.id());
        clusteringService.cluster(article.id());
    }

    private Article enrich(Article article) {
        articleService.updateProcessingStatus(article.id(), ProcessingStatus.PROCESSING)
                .orElseThrow(() -> new IllegalStateException("Article disappeared during processing"));

        String content = inputPolicy.prepare(article.extractedContent());
        AiResult result = outputValidator.validate(aiProvider.enrich(
                new AiInput(article.title(), content, article.originalLanguage())));
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                result.summary(),
                result.topics(),
                result.keywords(),
                result.entities().stream().map(this::toArticleEntity).toList(),
                properties.model(),
                properties.promptVersion(),
                clock.instant());

        Article completed = articleService.completeEnrichment(
                        article.id(), enrichment, result.category())
                .orElseThrow(() -> new IllegalStateException(
                        "Article disappeared during enrichment"));
        invalidateFeedCache(article.id());
        return completed;
    }

    public void markRetrying(String articleId) {
        articleService.updateProcessingStatus(articleId, ProcessingStatus.RETRYING);
    }

    public void markFailed(String articleId) {
        articleService.updateProcessingStatus(articleId, ProcessingStatus.FAILED);
    }

    private void invalidateFeedCache(String articleId) {
        try {
            feedCache.invalidate();
        } catch (RuntimeException exception) {
            LOGGER.warn("article_feed_cache_invalidation_failed articleId={} reason={}",
                    articleId, exception.getClass().getSimpleName());
        }
    }

    private void validateEvent(ArticleDiscoveredEvent event, Article article) {
        if (!article.sourceId().equals(event.sourceId())
                || event.eventVersion() != ArticleDiscoveredEvent.CURRENT_VERSION) {
            throw new IllegalArgumentException("Article event metadata is invalid");
        }
    }

    private ArticleEntity toArticleEntity(AiEntity entity) {
        return new ArticleEntity(entity.name(), entity.type());
    }
}
