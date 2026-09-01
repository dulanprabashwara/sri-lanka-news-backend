package lk.srilankannews.translation;

import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class TranslationBackfill implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationBackfill.class);
    private static final int MAX_SCAN = 1000;

    private final ArticleRepository repository;
    private final ArticleTranslationService translationService;
    private final TranslationProperties properties;

    public TranslationBackfill(
            ArticleRepository repository,
            ArticleTranslationService translationService,
            TranslationProperties properties) {
        this.repository = repository;
        this.translationService = translationService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        int limit = properties.backfillLimit();
        if (limit == 0) {
            LOGGER.info("multilingual_backfill_disabled");
            return;
        }
        List<Article> candidates = repository.findByAiEnrichmentIsNotNull(PageRequest.of(
                0, MAX_SCAN, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))));
        int processed = 0;
        int failed = 0;
        for (Article article : candidates) {
            if (processed >= limit) {
                break;
            }
            if (translationService.needsTranslation(article)) {
                try {
                    translationService.ensureTranslations(article.id());
                    processed++;
                } catch (RuntimeException exception) {
                    failed++;
                    LOGGER.warn("multilingual_backfill_failed articleId={} reason={}",
                            article.id(), exception.getClass().getSimpleName());
                }
            }
        }
        LOGGER.info("multilingual_backfill_complete translated={} failed={} limit={}",
                processed, failed, limit);
    }
}
