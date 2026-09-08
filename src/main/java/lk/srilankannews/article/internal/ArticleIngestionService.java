package lk.srilankannews.article.internal;

import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.CreateArticleCommand;
import lk.srilankannews.article.DuplicateArticleCanonicalUrlException;
import lk.srilankannews.article.DuplicateArticleContentException;
import lk.srilankannews.article.cache.ArticleFeedCache;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.processing.ArticleDiscoveredNotifier;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ArticleIngestionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleIngestionService.class);

    private final ArticleService articleService;
    private final SourceService sourceService;
    private final ArticleDiscoveredNotifier discoveredNotifier;
    private final ArticleFeedCache feedCache;

    public ArticleIngestionService(
            ArticleService articleService,
            SourceService sourceService,
            ArticleDiscoveredNotifier discoveredNotifier,
            ArticleFeedCache feedCache) {
        this.articleService = articleService;
        this.sourceService = sourceService;
        this.discoveredNotifier = discoveredNotifier;
        this.feedCache = feedCache;
    }

    public ArticleIngestionResponse ingest(ArticleIngestionRequest request) {
        Source source = sourceService.findBySlug(request.sourceSlug())
                .orElseThrow(() -> new ResourceNotFoundException("Source"));

        lk.srilankannews.article.ArticleLeadMedia validLeadMedia = null;
        if (source.imagePolicy().enabled() && request.leadMedia() != null) {
            try {
                java.net.URI uri = new java.net.URI(request.leadMedia().url());
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) && host != null) {
                    if (uri.getUserInfo() != null || isUnsafeHost(host)) {
                        // Drop explicitly forbidden host structures (though they wouldn't match whitelist anyway)
                    } else {
                        boolean allowed = source.imagePolicy().allowedHosts().stream()
                                .anyMatch(allowedHost -> host.equalsIgnoreCase(allowedHost));
                        if (allowed) {
                            validLeadMedia = new lk.srilankannews.article.ArticleLeadMedia(
                                    request.leadMedia().url(),
                                    lk.srilankannews.article.MediaType.IMAGE,
                                    request.leadMedia().altText(),
                                    request.leadMedia().caption(),
                                    request.leadMedia().credit(),
                                    request.leadMedia().width(),
                                    request.leadMedia().height(),
                                    request.leadMedia().discoveredFrom()
                            );
                        }
                    }
                }
            } catch (java.net.URISyntaxException ignored) {
            }
        }

        Article existing = articleService.findByCanonicalUrl(request.canonicalUrl()).orElse(null);
        boolean isUrlDuplicate = existing != null;
        if (existing == null) {
            existing = articleService.findByExtractedContent(request.extractedContent()).orElse(null);
        }
        if (existing != null) {
            if (existing.leadMedia() == null && validLeadMedia != null) {
                articleService.updateLeadMedia(existing.id(), validLeadMedia);
                invalidateFeedCache(existing.id());
                LOGGER.info("article_media_enriched articleId={}", existing.id());
            }
            return duplicate(existing, isUrlDuplicate ? ArticleIngestionResponse.DuplicateReason.URL_DUPLICATE : ArticleIngestionResponse.DuplicateReason.CONTENT_DUPLICATE);
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
                request.extractedContent(),
                validLeadMedia);

        try {
            Article created = articleService.create(command);
            invalidateFeedCache(created.id());
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

    private void invalidateFeedCache(String articleId) {
        try {
            feedCache.invalidate();
        } catch (RuntimeException exception) {
            LOGGER.warn("article_feed_cache_invalidation_failed articleId={} reason={}",
                    articleId, exception.getClass().getSimpleName());
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

    private boolean isUnsafeHost(String host) {
        String h = host.toLowerCase();
        if (h.equals("localhost")) return true;
        // IPv4 explicit ranges
        if (h.equals("127.0.0.1") || h.startsWith("127.")) return true;
        if (h.startsWith("10.")) return true;
        if (h.startsWith("172.")) return true;
        if (h.startsWith("192.168.")) return true;
        if (h.startsWith("169.254.")) return true;
        if (h.startsWith("0.")) return true;

        // IPv6 explicit ranges
        if (h.contains(":")) {
            String clean = h.replace("[", "").replace("]", "");
            if (clean.equals("::1") || clean.equals("::")) return true;
            if (clean.startsWith("fc") || clean.startsWith("fd")) return true; // fc00::/7
            if (clean.startsWith("fe8") || clean.startsWith("fe9") || clean.startsWith("fea") || clean.startsWith("feb")) return true; // fe80::/10
        }
        return false;
    }
}
