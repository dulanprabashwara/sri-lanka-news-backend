package lk.srilankannews.article;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lk.srilankannews.source.SourceService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final SourceService sourceService;
    private final ArticleContentHasher contentHasher;
    private final Clock clock;

    public ArticleService(
            ArticleRepository articleRepository,
            SourceService sourceService,
            ArticleContentHasher contentHasher,
            Clock clock) {
        this.articleRepository = articleRepository;
        this.sourceService = sourceService;
        this.contentHasher = contentHasher;
        this.clock = clock;
    }

    public Article create(@Valid CreateArticleCommand command) {
        if (!sourceService.existsById(command.sourceId())) {
            throw new UnknownArticleSourceException(command.sourceId());
        }
        if (articleRepository.existsByCanonicalUrl(command.canonicalUrl())) {
            throw new DuplicateArticleCanonicalUrlException(command.canonicalUrl());
        }
        String contentHash = contentHasher.hash(command.extractedContent());
        if (articleRepository.existsByContentHash(contentHash)) {
            throw new DuplicateArticleContentException(contentHash);
        }

        Instant now = clock.instant();
        try {
            return articleRepository.save(Article.create(command, contentHash, now));
        } catch (DuplicateKeyException exception) {
            if (articleRepository.existsByCanonicalUrl(command.canonicalUrl())) {
                throw new DuplicateArticleCanonicalUrlException(command.canonicalUrl());
            }
            if (articleRepository.existsByContentHash(contentHash)) {
                throw new DuplicateArticleContentException(contentHash);
            }
            throw exception;
        }
    }

    public Optional<Article> findById(String id) {
        return articleRepository.findById(id);
    }

    public Optional<Article> findByCanonicalUrl(String canonicalUrl) {
        return articleRepository.findByCanonicalUrl(canonicalUrl);
    }

    public Optional<Article> findByExtractedContent(String extractedContent) {
        return articleRepository.findByContentHash(contentHasher.hash(extractedContent));
    }

    public Optional<Article> updateProcessingStatus(String articleId, ProcessingStatus status) {
        return articleRepository.findById(articleId)
                .map(article -> articleRepository.save(
                        article.withProcessingStatus(status, clock.instant())));
    }

    public Optional<Article> completeEnrichment(
            String articleId, ArticleAiEnrichment enrichment, ArticleCategory category) {
        return articleRepository.findById(articleId)
                .map(article -> articleRepository.save(
                        article.withAiEnrichment(enrichment, category, clock.instant())));
    }

    public java.util.List<Article> findAwaitingProcessing() {
        return articleRepository.findAwaitingProcessing();
    }

    public Page<Article> findAll(ArticleFilter filter, Pageable pageable) {
        return articleRepository.findAll(filter, pageable);
    }
}
