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

    private synchronized void startConsumer() {
        if (started.get()) {
            return;
        }
        Subscription subscription = null;
        try {
            groupManager.ensureConsumerGroup();
            subscription = container.receive(
                    Consumer.from(properties.consumerGroup(), properties.consumerName()),
                    StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed()),
                    listener);
            container.start();
            started.set(true);
            LOGGER.info("article_stream_consumer_started stream={} group={} consumer={}",
                    properties.streamKey(), properties.consumerGroup(), properties.consumerName());
        } catch (RuntimeException exception) {
            if (subscription != null) {
                subscription.cancel();
            }
            RedisFailureDescription.Details details = RedisFailureDescription.from(exception);
            LOGGER.error(
                    "article_stream_consumer_unavailable reason={} rootCause={} detail={}",
                    exception.getClass().getSimpleName(),
                    details.rootCause(),
                    details.message());
            scheduleRetry();
        }
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
