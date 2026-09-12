package lk.srilankannews.notifications;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Order(210)
public class PendingNotificationEventRecovery implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingNotificationEventRecovery.class);

    private final StringRedisTemplate redisTemplate;
    private final NotificationEventConsumer consumer;
    private final NotificationRedisProperties redisProperties;
    private final PendingNotificationRecoveryProperties recoveryProperties;

    public PendingNotificationEventRecovery(
            StringRedisTemplate redisTemplate,
            NotificationEventConsumer consumer,
            NotificationRedisProperties redisProperties,
            PendingNotificationRecoveryProperties recoveryProperties) {
        this.redisTemplate = redisTemplate;
        this.consumer = consumer;
        this.redisProperties = redisProperties;
        this.recoveryProperties = recoveryProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        recoverStalePendingEvents();
    }

    @Scheduled(fixedDelayString = "${news.notifications.pending-recovery.fixed-delay:1m}")
    public int recoverScheduled() {
        if (!recoveryProperties.enabled()) {
            return 0;
        }
        return recoverStalePendingEvents();
    }

    public synchronized int recoverStalePendingEvents() {
        if (!recoveryProperties.enabled()) {
            return 0;
        }
        try {
            String streamKey = redisProperties.streamKey();
            String group = redisProperties.consumerGroup();
            String consumerName = redisProperties.consumerName();
            Duration minIdleTime = recoveryProperties.staleAfter();
            int batchSize = recoveryProperties.batchSize();

            Boolean hasKey = redisTemplate.hasKey(streamKey);
            if (Boolean.FALSE.equals(hasKey)) {
                return 0;
            }

            int totalRecovered = 0;
            int maxBatches = 10;
            int currentBatch = 0;

            while (currentBatch < maxBatches) {
                currentBatch++;
                PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                        streamKey,
                        group,
                        Range.unbounded(),
                        batchSize
                );

                if (pendingMessages == null || pendingMessages.isEmpty()) {
                    break;
                }

                List<RecordId> staleIds = new ArrayList<>();
                for (PendingMessage msg : pendingMessages) {
                    if (msg.getElapsedTimeSinceLastDelivery() != null
                            && msg.getElapsedTimeSinceLastDelivery().compareTo(minIdleTime) >= 0) {
                        staleIds.add(msg.getId());
                    }
                }

                if (staleIds.isEmpty()) {
                    break;
                }

                LOGGER.info("notification_pending_recovery_started count={}", staleIds.size());

                List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                        streamKey,
                        group,
                        consumerName,
                        minIdleTime,
                        staleIds.toArray(new RecordId[0])
                );

                if (claimed == null || claimed.isEmpty()) {
                    break;
                }

                int batchProcessed = 0;
                for (MapRecord<String, Object, Object> record : claimed) {
                    LOGGER.debug("notification_pending_claimed recordId={}", record.getId().getValue());
                    consumer.handleRecord(record);
                    batchProcessed++;
                }

                totalRecovered += batchProcessed;
                LOGGER.info("notification_pending_recovery_completed recovered={}", batchProcessed);

                if (staleIds.size() < batchSize) {
                    break;
                }
            }

            return totalRecovered;
        } catch (Exception e) {
            LOGGER.error("notification_pending_recovery_failed reason={} message={}",
                    e.getClass().getSimpleName(), e.getMessage());
            return 0;
        }
    }
}
