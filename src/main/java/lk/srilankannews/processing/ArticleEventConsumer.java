package lk.srilankannews.processing;

import lk.srilankannews.ai.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

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
            logRetry(event, exception, reason);
        } else {
            worker.markFailed(event.articleId());
            transferred = stream.publishDeadLetter(event, reason);
            logDeadLetter(event, exception, reason);
        }
        if (transferred) {
            stream.acknowledge(recordId);
        } else {
            LOGGER.error("article_event_unacknowledged eventId={} articleId={} recordId={}",
                    event.eventId(), event.articleId(), recordId);
        }
    }

    private void logRetry(
            ArticleDiscoveredEvent event, RuntimeException exception, String reason) {
        if (exception instanceof AiProviderException aiException) {
            LOGGER.warn(
                    "article_event_retry eventId={} articleId={} attempt={} reason={} "
                            + "providerCategory={} httpStatus={} providerCode={} "
                            + "providerMessage={} model={}",
                    event.eventId(),
                    event.articleId(),
                    event.attempt(),
                    reason,
                    aiException.kind(),
                    aiException.httpStatus(),
                    aiException.providerCode(),
                    aiException.providerMessage(),
                    aiException.model());
            return;
        }
        LOGGER.warn("article_event_retry eventId={} articleId={} attempt={} reason={}",
                event.eventId(), event.articleId(), event.attempt(), reason);
    }

    private void logDeadLetter(
            ArticleDiscoveredEvent event, RuntimeException exception, String reason) {
        if (exception instanceof AiProviderException aiException) {
            LOGGER.error(
                    "article_event_dead_letter eventId={} articleId={} attempts={} reason={} "
                            + "providerCategory={} httpStatus={} providerCode={} "
                            + "providerMessage={} model={}",
                    event.eventId(),
                    event.articleId(),
                    event.attempt(),
                    reason,
                    aiException.kind(),
                    aiException.httpStatus(),
                    aiException.providerCode(),
                    aiException.providerMessage(),
                    aiException.model());
            return;
        }
        LOGGER.error("article_event_dead_letter eventId={} articleId={} attempts={} reason={}",
                event.eventId(), event.articleId(), event.attempt(), reason);
    }
}
