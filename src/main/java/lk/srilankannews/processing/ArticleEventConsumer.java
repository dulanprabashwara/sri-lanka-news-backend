package lk.srilankannews.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@ConditionalOnProperty(
        name = "news.processing.redis.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleEventConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleEventConsumer.class);
    private final ArticleProcessingWorker worker;
    private final ArticleEventStream stream;
    private final RedisProcessingProperties properties;

    public ArticleEventConsumer(
            ArticleProcessingWorker worker,
            ArticleEventStream stream,
            RedisProcessingProperties properties) {
        this.worker = worker;
        this.stream = stream;
        this.properties = properties;
    }

    public void consume(String recordId, ArticleDiscoveredEvent event) {
        try {
            worker.process(event);
            stream.acknowledge(recordId);
            LOGGER.info("article_event_completed eventId={} articleId={} attempt={}",
                    event.eventId(), event.articleId(), event.attempt());
        } catch (RuntimeException exception) {
            handleFailure(recordId, event, exception);
        }
    }

    private void handleFailure(
            String recordId, ArticleDiscoveredEvent event, RuntimeException exception) {
        String reason = exception.getClass().getSimpleName();
        boolean transferred;
        if (event.attempt() < properties.maxAttempts()) {
            worker.markRetrying(event.articleId());
            transferred = stream.publish(event.nextAttempt());
            LOGGER.warn("article_event_retry eventId={} articleId={} attempt={} reason={}",
                    event.eventId(), event.articleId(), event.attempt(), reason);
        } else {
            worker.markFailed(event.articleId());
            transferred = stream.publishDeadLetter(event, reason);
            LOGGER.error("article_event_dead_letter eventId={} articleId={} attempts={} reason={}",
                    event.eventId(), event.articleId(), event.attempt(), reason);
        }
        if (transferred) {
            stream.acknowledge(recordId);
        } else {
            LOGGER.error("article_event_unacknowledged eventId={} articleId={} recordId={}",
                    event.eventId(), event.articleId(), recordId);
        }
    }
}
