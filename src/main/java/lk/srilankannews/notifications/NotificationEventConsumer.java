package lk.srilankannews.notifications;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.stereotype.Service;

@Service
public class NotificationEventConsumer implements InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private static final String STREAM_KEY = NotificationEventPublisher.STREAM_KEY;
    private static final String CONSUMER_GROUP = "notification-processing";
    private static final String CONSUMER_NAME = "worker-1";

    private final StringRedisTemplate redisTemplate;
    private final NotificationProcessingService processingService;
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;

    public NotificationEventConsumer(StringRedisTemplate redisTemplate,
                                     NotificationProcessingService processingService) {
        this.redisTemplate = redisTemplate;
        this.processingService = processingService;
    }

    @Override
    public void afterPropertiesSet() {
        createGroupIfNotExists();

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .build();

        listenerContainer = StreamMessageListenerContainer.create(redisTemplate.getConnectionFactory(), options);

        listenerContainer.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                this::handleRecord
        );

        listenerContainer.start();
    }

    private void createGroupIfNotExists() {
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(STREAM_KEY))) {
                redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
            } else {
                redisTemplate.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
            }
        } catch (Exception e) {
            // Group might already exist
            LOGGER.debug("Consumer group creation skipped: {}", e.getMessage());
        }
    }

    private void handleRecord(MapRecord<String, String, String> record) {
        String eventId = record.getValue().get("eventId");
        LOGGER.debug("Received notification event: {}", eventId);
        try {
            processingService.processEvent(record.getValue());
            redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, record.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to process notification event {}", eventId, e);
        }
    }

    @Override
    public void destroy() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }
}
