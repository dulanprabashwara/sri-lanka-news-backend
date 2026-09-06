package lk.srilankannews.notifications;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lk.srilankannews.retention.RetentionPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventPublisher.class);
    public static final String STREAM_KEY = "notification-events";

    private final StringRedisTemplate redisTemplate;
    private final NotificationEventRepository eventRepository;
    private final RetentionPolicyService retentionPolicyService;
    private final Clock clock;

    public NotificationEventPublisher(StringRedisTemplate redisTemplate,
                                      NotificationEventRepository eventRepository,
                                      RetentionPolicyService retentionPolicyService,
                                      Clock clock) {
        this.redisTemplate = redisTemplate;
        this.eventRepository = eventRepository;
        this.retentionPolicyService = retentionPolicyService;
        this.clock = clock;
    }

    public void publish(NotificationEvent event) {
        try {
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .in(STREAM_KEY)
                    .ofMap(Map.of(
                            "eventId", event.id(),
                            "articleId", event.articleId(),
                            "storyId", event.storyId(),
                            "occurredAt", event.occurredAt().toString(),
                            "eventVersion", event.eventVersion(),
                            "attempt", String.valueOf(event.attemptCount())
                    ));

            RecordId recordId = redisTemplate.opsForStream().add(record);

            Instant publishedAt = clock.instant();
            Instant expiresAt = retentionPolicyService.calculateNotificationOutboxExpiry(NotificationEvent.EventStatus.PUBLISHED, publishedAt).orElse(null);

            NotificationEvent updated = new NotificationEvent(
                    event.id(),
                    event.articleId(),
                    event.storyId(),
                    event.sourceId(),
                    event.occurredAt(),
                    event.eventVersion(),
                    NotificationEvent.EventStatus.PUBLISHED,
                    event.attemptCount(),
                    null, // nextAttemptAt
                    publishedAt,
                    event.processedAt(),
                    event.lastErrorCode(),
                    expiresAt
            );
            eventRepository.save(updated);

            LOGGER.debug("Published notification event {} to stream {}, redis id: {}", event.id(), STREAM_KEY, recordId);
        } catch (Exception e) {
            LOGGER.error("Failed to publish notification event {} to Redis", event.id(), e);
            scheduleRetry(event, e.getMessage());
        }
    }

    private void scheduleRetry(NotificationEvent event, String error) {
        int attempt = event.attemptCount() + 1;
        if (attempt > 5) {
            // NotificationEvent lacks a failedAt canonical timestamp field, so expiresAt remains null
            eventRepository.save(new NotificationEvent(
                    event.id(),
                    event.articleId(),
                    event.storyId(),
                    event.sourceId(),
                    event.occurredAt(),
                    event.eventVersion(),
                    NotificationEvent.EventStatus.FAILED,
                    attempt,
                    null,
                    event.publishedAt(),
                    event.processedAt(),
                    error != null ? error : "Unknown publishing error",
                    null
            ));
        } else {
            eventRepository.save(new NotificationEvent(
                    event.id(),
                    event.articleId(),
                    event.storyId(),
                    event.sourceId(),
                    event.occurredAt(),
                    event.eventVersion(),
                    NotificationEvent.EventStatus.RETRYING,
                    attempt,
                    clock.instant().plus(Duration.ofMinutes(2L * attempt)), // simple backoff
                    event.publishedAt(),
                    event.processedAt(),
                    error,
                    null // active RETRYING events do not expire
            ));
        }
    }
}
