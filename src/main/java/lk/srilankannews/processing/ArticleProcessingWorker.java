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
import org.springframework.stereotype.Service;

@Service
public class ArticleProcessingWorker {
    private final ArticleService articleService;
    private final AiProvider aiProvider;
    private final AiInputPolicy inputPolicy;
    private final AiOutputValidator outputValidator;
    private final GeminiProperties properties;
    private final Clock clock;

    public ArticleProcessingWorker(
            ArticleService articleService,
            AiProvider aiProvider,
            AiInputPolicy inputPolicy,
            AiOutputValidator outputValidator,
            GeminiProperties properties,
            Clock clock) {
        this.articleService = articleService;
        this.aiProvider = aiProvider;
        this.inputPolicy = inputPolicy;
        this.outputValidator = outputValidator;
        this.properties = properties;
        this.clock = clock;
    }

    public void process(ArticleDiscoveredEvent event) {
        Article article = articleService.findById(event.articleId())
                .orElseThrow(() -> new IllegalStateException("Article does not exist"));
        validateEvent(event, article);
        if (article.processingStatus() == ProcessingStatus.COMPLETED
                && article.aiEnrichment() != null
                && article.aiEnrichment().matches(properties.model(), properties.promptVersion())) {
            return;
        }

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

        articleService.completeEnrichment(article.id(), enrichment, result.category())
                .orElseThrow(() -> new IllegalStateException("Article disappeared during enrichment"));
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

    private ArticleEntity toArticleEntity(AiEntity entity) {
        return new ArticleEntity(entity.name(), entity.type());
    }
}
