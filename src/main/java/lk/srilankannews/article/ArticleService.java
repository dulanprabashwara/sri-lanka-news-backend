package lk.srilankannews.article;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lk.srilankannews.source.SourceService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final SourceService sourceService;
    private final Clock clock;

    public ArticleService(ArticleRepository articleRepository, SourceService sourceService, Clock clock) {
        this.articleRepository = articleRepository;
        this.sourceService = sourceService;
        this.clock = clock;
    }

    public Article create(@Valid CreateArticleCommand command) {
        if (!sourceService.existsById(command.sourceId())) {
            throw new UnknownArticleSourceException(command.sourceId());
        }
        if (articleRepository.existsByCanonicalUrl(command.canonicalUrl())) {
            throw new DuplicateArticleCanonicalUrlException(command.canonicalUrl());
        }

        Instant now = clock.instant();
        try {
            return articleRepository.save(Article.create(command, now));
        } catch (DuplicateKeyException exception) {
            throw new DuplicateArticleCanonicalUrlException(command.canonicalUrl());
        }
    }

    public Optional<Article> findById(String id) {
        return articleRepository.findById(id);
    }

    public Optional<Article> findByCanonicalUrl(String canonicalUrl) {
        return articleRepository.findByCanonicalUrl(canonicalUrl);
    }
}
