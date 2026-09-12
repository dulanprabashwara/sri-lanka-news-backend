package lk.srilankannews.processing;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@ConditionalOnProperty(
        name = "news.processing.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamStartup implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisStreamStartup.class);
    private final RedisStreamGroupManager groupManager;
    private final RedisProcessingProperties properties;
    private final RedisArticleStreamListener listener;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final TaskScheduler retryScheduler;
    private final AtomicBoolean started = new AtomicBoolean();
    private Subscription subscription;

    public RedisStreamStartup(
            RedisStreamGroupManager groupManager,
            RedisProcessingProperties properties,
            RedisArticleStreamListener listener,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
            @Qualifier("articleStreamStartupScheduler") TaskScheduler retryScheduler) {
        this.groupManager = groupManager;
        this.properties = properties;
        this.listener = listener;
        this.container = container;
        this.retryScheduler = retryScheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        startConsumer();
    }

    @Scheduled(fixedDelayString = "${news.processing.redis.supervise-interval:30s}")
    public synchronized void superviseSubscription() {
        if (isSubscribed()) {
            return;
        }
        LOGGER.warn("article_stream_subscription_inactive_restarting stream={} group={} consumer={}",
                properties.streamKey(), properties.consumerGroup(), properties.consumerName());
        startConsumer();
    }

    public synchronized boolean isSubscribed() {
        return subscription != null && subscription.isActive() && container.isRunning();
    }

    public synchronized void startConsumer() {
        if (isSubscribed()) {
            return;
        }
        cleanupSubscription();
        try {
            groupManager.ensureConsumerGroup();
            this.subscription = container.receive(
                    Consumer.from(properties.consumerGroup(), properties.consumerName()),
                    StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed()),
                    listener);
            if (!container.isRunning()) {
                container.start();
            }
            started.set(true);
            LOGGER.info("article_stream_consumer_started stream={} group={} consumer={}",
                    properties.streamKey(), properties.consumerGroup(), properties.consumerName());
        } catch (RuntimeException exception) {
            if (subscription != null) {
                subscription.cancel();
            }
            cleanupSubscription();
            started.set(false);
            RedisFailureDescription.Details details = RedisFailureDescription.from(exception);
            LOGGER.error(
                    "article_stream_consumer_unavailable reason={} rootCause={} detail={}",
                    exception.getClass().getSimpleName(),
                    details.rootCause(),
                    details.message());
            scheduleRetry();
        }
    }

    private void cleanupSubscription() {
        if (subscription != null) {
            try {
                subscription.cancel();
            } catch (Exception exception) {
                LOGGER.warn("article_stream_subscription_cancel_failed reason={}", exception.getMessage());
            }
            try {
                container.remove(subscription);
            } catch (Exception exception) {
                LOGGER.debug("article_stream_subscription_remove_failed reason={}", exception.getMessage());
            }
            subscription = null;
        }
    }

    Subscription getSubscription() {
        return subscription;
    }

    private void scheduleRetry() {
        Instant retryAt = Instant.now().plus(properties.pollTimeout());
        try {
            retryScheduler.schedule(this::startConsumer, retryAt);
            LOGGER.info("article_stream_consumer_retry_scheduled retryAt={}", retryAt);
        } catch (RuntimeException exception) {
            LOGGER.error("article_stream_consumer_retry_schedule_failed reason={}",
                    exception.getClass().getSimpleName());
        }
    }
}
