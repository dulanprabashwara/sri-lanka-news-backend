package lk.srilankannews.notifications;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationEventConsumer implements InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final StringRedisTemplate redisTemplate;
    private final NotificationProcessingService processingService;
    private final NotificationRedisProperties properties;
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private Subscription subscription;
    private final AtomicBoolean started = new AtomicBoolean();

    @Autowired
    public NotificationEventConsumer(StringRedisTemplate redisTemplate,
                                     NotificationProcessingService processingService,
                                     NotificationRedisProperties properties) {
        this.redisTemplate = redisTemplate;
        this.processingService = processingService;
        this.properties = properties != null ? properties : new NotificationRedisProperties();
    }

    public NotificationEventConsumer(StringRedisTemplate redisTemplate,
                                     NotificationProcessingService processingService) {
        this(redisTemplate, processingService, new NotificationRedisProperties());
    }

    @Override
    public void afterPropertiesSet() {
        startConsumer();
    }

    public synchronized boolean isSubscribed() {
        return subscription != null && subscription.isActive() && listenerContainer != null && listenerContainer.isRunning();
    }

    public synchronized void startConsumer() {
        if (isSubscribed()) {
            return;
        }
        cleanupSubscription();
        try {
            createGroupIfNotExists();

            if (listenerContainer == null) {
                StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                        StreamMessageListenerContainerOptions.builder()
                                .pollTimeout(properties.pollTimeout())
                                .errorHandler(throwable -> {
                                    LOGGER.error("notification_stream_listener_error reason={} message={}",
                                            throwable.getClass().getSimpleName(),
                                            throwable.getMessage());
                                })
                                .build();

                listenerContainer = StreamMessageListenerContainer.create(redisTemplate.getConnectionFactory(), options);
            }

            this.subscription = listenerContainer.receive(
                    Consumer.from(properties.consumerGroup(), properties.consumerName()),
                    StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed()),
                    this::handleRecord
            );

            if (!listenerContainer.isRunning()) {
                listenerContainer.start();
            }
            started.set(true);
            LOGGER.info("notification_stream_subscribed stream={} group={} consumer={}",
                    properties.streamKey(), properties.consumerGroup(), properties.consumerName());
        } catch (Exception e) {
            cleanupSubscription();
            started.set(false);
            LOGGER.error("notification_stream_subscribe_failed reason={} message={}",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${news.notifications.redis.supervise-interval:30s}")
    public synchronized void superviseSubscription() {
        if (isSubscribed()) {
            return;
        }
        LOGGER.warn("notification_stream_subscription_unhealthy stream={} group={} consumer={}",
                properties.streamKey(), properties.consumerGroup(), properties.consumerName());
        LOGGER.info("notification_stream_resubscribe_started stream={} group={} consumer={}",
                properties.streamKey(), properties.consumerGroup(), properties.consumerName());
        startConsumer();
        if (isSubscribed()) {
            LOGGER.info("notification_stream_resubscribed stream={} group={} consumer={}",
                    properties.streamKey(), properties.consumerGroup(), properties.consumerName());
        }
    }

    private synchronized void cleanupSubscription() {
        if (subscription != null) {
            try {
                subscription.cancel();
            } catch (Exception e) {
                LOGGER.debug("Error cancelling notification subscription: {}", e.getMessage());
            }
            subscription = null;
        }
    }

    private void createGroupIfNotExists() {
        String streamKey = properties.streamKey();
        String consumerGroup = properties.consumerGroup();
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(streamKey))) {
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), consumerGroup);
            } else {
                redisTemplate.opsForStream().createGroup(streamKey, consumerGroup);
            }
        } catch (Exception e) {
            // Group might already exist
            LOGGER.debug("Consumer group creation skipped: {}", e.getMessage());
        }
    }

    public void handleRecord(MapRecord<String, ?, ?> record) {
        java.util.Map<String, String> payload = new java.util.HashMap<>();
        if (record.getValue() != null) {
            record.getValue().forEach((k, v) -> {
                if (k != null && v != null) {
                    payload.put(k.toString(), v.toString());
                }
            });
        }
        String eventId = payload.get("eventId");
        LOGGER.debug("Received notification event: {}", eventId);
        try {
            processingService.processEvent(payload);
            redisTemplate.opsForStream().acknowledge(properties.streamKey(), properties.consumerGroup(), record.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to process notification event {}", eventId, e);
        }
    }

    @Override
    public synchronized void destroy() {
        cleanupSubscription();
        if (listenerContainer != null) {
            try {
                listenerContainer.stop();
            } catch (Exception e) {
                LOGGER.debug("Error stopping notification listener container: {}", e.getMessage());
            }
        }
    }
}
