package lk.srilankannews.article.internal;

import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.CreateArticleCommand;
import lk.srilankannews.article.DuplicateArticleCanonicalUrlException;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.springframework.stereotype.Service;

@Service
public class ArticleIngestionService {

    private final ArticleService articleService;
    private final SourceService sourceService;

    public ArticleIngestionService(ArticleService articleService, SourceService sourceService) {
        this.articleService = articleService;
        this.sourceService = sourceService;
    }

    public ArticleIngestionResponse ingest(ArticleIngestionRequest request) {
        Source source = sourceService.findBySlug(request.sourceSlug())
                .orElseThrow(() -> new ResourceNotFoundException("Source"));

        Article existing = articleService.findByCanonicalUrl(request.canonicalUrl()).orElse(null);
        if (existing != null) {
            return duplicate(existing);
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
            return new ArticleIngestionResponse(
                    ArticleIngestionResponse.Status.CREATED,
                    created.id(),
                    created.canonicalUrl());
        } catch (DuplicateArticleCanonicalUrlException exception) {
            return articleService.findByCanonicalUrl(request.canonicalUrl())
                    .map(this::duplicate)
                    .orElseGet(() -> new ArticleIngestionResponse(
                            ArticleIngestionResponse.Status.DUPLICATE,
                            null,
                            request.canonicalUrl()));
        }
    }

    private ArticleIngestionResponse duplicate(Article article) {
        return new ArticleIngestionResponse(
                ArticleIngestionResponse.Status.DUPLICATE,
                article.id(),
                article.canonicalUrl());
    }
}
