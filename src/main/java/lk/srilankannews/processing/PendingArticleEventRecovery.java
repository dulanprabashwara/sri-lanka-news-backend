package lk.srilankannews.processing;

import lk.srilankannews.article.ArticleService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(200)
@ConditionalOnProperty(
        name = "news.processing.redis.enabled", havingValue = "true", matchIfMissing = true)
public class PendingArticleEventRecovery implements ApplicationRunner {
    private final ArticleService articleService;
    private final ArticleDiscoveredNotifier notifier;

    public PendingArticleEventRecovery(
            ArticleService articleService, ArticleDiscoveredNotifier notifier) {
        this.articleService = articleService;
        this.notifier = notifier;
    }

    @Override
    public void run(ApplicationArguments args) {
        articleService.findAwaitingProcessing().forEach(notifier::notifyDiscovered);
    }
}
