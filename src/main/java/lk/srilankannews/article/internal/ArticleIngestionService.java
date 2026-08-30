package lk.srilankannews.article.internal;

import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.CreateArticleCommand;
import lk.srilankannews.article.DuplicateArticleCanonicalUrlException;
import lk.srilankannews.article.DuplicateArticleContentException;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.processing.ArticleDiscoveredNotifier;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ArticleIngestionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleIngestionService.class);

    private final ArticleService articleService;
    private final SourceService sourceService;
    private final ArticleDiscoveredNotifier discoveredNotifier;

    public ArticleIngestionService(
            ArticleService articleService,
            SourceService sourceService,
            ArticleDiscoveredNotifier discoveredNotifier) {
        this.articleService = articleService;
        this.sourceService = sourceService;
        this.discoveredNotifier = discoveredNotifier;
    }

    public ArticleIngestionResponse ingest(ArticleIngestionRequest request) {
        Source source = sourceService.findBySlug(request.sourceSlug())
                .orElseThrow(() -> new ResourceNotFoundException("Source"));

        Article existing = articleService.findByCanonicalUrl(request.canonicalUrl()).orElse(null);
        if (existing != null) {
            return duplicate(existing, ArticleIngestionResponse.DuplicateReason.URL_DUPLICATE);
        }

        existing = articleService.findByExtractedContent(request.extractedContent()).orElse(null);
        if (existing != null) {
            return duplicate(existing, ArticleIngestionResponse.DuplicateReason.CONTENT_DUPLICATE);
        }

        CreateArticleCommand command = new CreateArticleCommand(
                source.id(),
                request.title(),
                request.originalUrl(),
                request.canonicalUrl(),
                request.originalLanguage(),
                request.authors(),
                request.publishedAt(),
                request.discoveredAt(),
                request.category(),
                request.extractedContent());

        try {
            Article created = articleService.create(command);
            try {
                discoveredNotifier.notifyDiscovered(created);
            } catch (RuntimeException exception) {
                LOGGER.warn("article_event_notification_failed articleId={} reason={}",
                        created.id(), exception.getClass().getSimpleName());
            }
            return new ArticleIngestionResponse(
                    ArticleIngestionResponse.Status.CREATED,
                    created.id(),
                    created.canonicalUrl(),
                    null);
        } catch (DuplicateArticleCanonicalUrlException exception) {
            return articleService.findByCanonicalUrl(request.canonicalUrl())
                    .map(article -> duplicate(article, ArticleIngestionResponse.DuplicateReason.URL_DUPLICATE))
                    .orElseGet(() -> new ArticleIngestionResponse(
                            ArticleIngestionResponse.Status.DUPLICATE,
                            null,
                            request.canonicalUrl(),
                            ArticleIngestionResponse.DuplicateReason.URL_DUPLICATE));
        } catch (DuplicateArticleContentException exception) {
            return articleService.findByExtractedContent(request.extractedContent())
                    .map(article -> duplicate(article, ArticleIngestionResponse.DuplicateReason.CONTENT_DUPLICATE))
                    .orElseGet(() -> new ArticleIngestionResponse(
                            ArticleIngestionResponse.Status.DUPLICATE,
                            null,
                            request.canonicalUrl(),
                            ArticleIngestionResponse.DuplicateReason.CONTENT_DUPLICATE));
        }
    }

    private ArticleIngestionResponse duplicate(
            Article article, ArticleIngestionResponse.DuplicateReason duplicateReason) {
        return new ArticleIngestionResponse(
                ArticleIngestionResponse.Status.DUPLICATE,
                article.id(),
                article.canonicalUrl(),
                duplicateReason);
    }
}
