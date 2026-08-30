package lk.srilankannews.processing;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Executor;
import lk.srilankannews.article.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class ArticleDiscoveredNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleDiscoveredNotifier.class);
    private final ArticleEventPublisher publisher;
    private final Clock clock;
    private final Executor dispatchExecutor;

    public ArticleDiscoveredNotifier(
            ArticleEventPublisher publisher,
            Clock clock,
            @Qualifier("articleEventDispatchExecutor") Executor dispatchExecutor) {
        this.publisher = publisher;
        this.clock = clock;
        this.dispatchExecutor = dispatchExecutor;
    }

    public void notifyDiscovered(Article article) {
        ArticleDiscoveredEvent event = new ArticleDiscoveredEvent(
                UUID.randomUUID().toString(), article.id(), article.sourceId(), clock.instant(),
                ArticleDiscoveredEvent.CURRENT_VERSION, 1);
        try {
            dispatchExecutor.execute(() -> publish(article, event));
        } catch (RuntimeException exception) {
            LOGGER.warn("article_event_deferred articleId={} eventId={} reason={}",
                    article.id(), event.eventId(), exception.getClass().getSimpleName());
        }
    }

    private void publish(Article article, ArticleDiscoveredEvent event) {
        try {
            if (!publisher.publish(event)) {
                LOGGER.warn("article_event_deferred articleId={} eventId={}",
                        article.id(), event.eventId());
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("article_event_deferred articleId={} eventId={} reason={}",
                    article.id(), event.eventId(), exception.getClass().getSimpleName());
        }
    }
}
