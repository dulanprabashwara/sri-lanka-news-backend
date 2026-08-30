package lk.srilankannews.processing;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@ConditionalOnProperty(
        name = "news.processing.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamStartup implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisStreamStartup.class);
    private final StringRedisTemplate redis;
    private final RedisProcessingProperties properties;
    private final RedisArticleStreamListener listener;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    public RedisStreamStartup(
            StringRedisTemplate redis, RedisProcessingProperties properties,
            RedisArticleStreamListener listener,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        this.redis = redis;
        this.properties = properties;
        this.listener = listener;
        this.container = container;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            createConsumerGroupIfNeeded();
            container.receive(
                    Consumer.from(properties.consumerGroup(), properties.consumerName()),
                    StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed()),
                    listener);
            container.start();
            LOGGER.info("article_stream_consumer_started stream={} group={} consumer={}",
                    properties.streamKey(), properties.consumerGroup(), properties.consumerName());
        } catch (RuntimeException exception) {
            LOGGER.error("article_stream_consumer_unavailable reason={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void createConsumerGroupIfNeeded() {
        RecordId bootstrap = redis.opsForStream().add(
                StreamRecords.mapBacked(Map.of("bootstrap", "true"))
                        .withStreamKey(properties.streamKey()));
        try {
            redis.opsForStream().createGroup(
                    properties.streamKey(), ReadOffset.from("0-0"), properties.consumerGroup());
        } catch (RuntimeException exception) {
            if (exception.getMessage() == null || !exception.getMessage().contains("BUSYGROUP")) {
                throw exception;
            }
        } finally {
            if (bootstrap != null) {
                redis.opsForStream().delete(properties.streamKey(), bootstrap);
            }
        }
    }
}
