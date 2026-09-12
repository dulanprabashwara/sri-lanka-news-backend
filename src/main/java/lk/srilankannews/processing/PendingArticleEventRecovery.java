package lk.srilankannews.processing;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Order(200)
@ConditionalOnProperty(
        name = "news.processing.redis.enabled", havingValue = "true", matchIfMissing = true)
public class PendingArticleEventRecovery implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(PendingArticleEventRecovery.class);

    private final ArticleService articleService;
    private final ArticleDiscoveredNotifier notifier;
    private final PendingArticleRecoveryProperties properties;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PendingArticleEventRecovery(
            ArticleService articleService,
            ArticleDiscoveredNotifier notifier,
            PendingArticleRecoveryProperties properties,
            Clock clock) {
        this.articleService = articleService;
        this.notifier = notifier;
        this.properties = properties;
        this.clock = clock;
    }

    public PendingArticleEventRecovery(
            ArticleService articleService,
            ArticleDiscoveredNotifier notifier) {
        this(articleService, notifier, new PendingArticleRecoveryProperties(), Clock.systemUTC());
    }

    @Override
    public void run(ApplicationArguments args) {
        recoverStalePendingArticles();
    }

    @Scheduled(fixedDelayString = "${news.processing.pending-recovery.fixed-delay:5m}")
    public int recoverScheduled() {
        if (!properties.enabled()) {
            return 0;
        }
        return recoverStalePendingArticles();
    }

    public int recoverStalePendingArticles() {
        Instant threshold = clock.instant().minus(properties.staleAfter());
        PageRequest pageRequest = PageRequest.of(0, properties.batchSize(), Sort.by(Sort.Direction.ASC, "createdAt"));
        List<Article> staleArticles = articleService.findStalePendingArticles(threshold, pageRequest);
        if (staleArticles.isEmpty()) {
            LOGGER.debug("no_stale_pending_articles_found threshold={}", threshold);
            return 0;
        }

        LOGGER.info("stale_pending_articles_recovery_started count={} threshold={}",
                staleArticles.size(), threshold);
        int recovered = 0;
        for (Article article : staleArticles) {
            try {
                notifier.notifyDiscovered(article);
                recovered++;
            } catch (RuntimeException exception) {
                LOGGER.warn("stale_pending_article_recovery_failed articleId={} reason={}",
                        article.id(), exception.getClass().getSimpleName());
            }
        }
        LOGGER.info("stale_pending_articles_recovery_completed recovered={}", recovered);
        return recovered;
    }
}
