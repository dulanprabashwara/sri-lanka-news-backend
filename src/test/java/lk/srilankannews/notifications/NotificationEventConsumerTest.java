package lk.srilankannews.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @Mock
    private NotificationProcessingService processingService;

    private NotificationRedisProperties properties;
    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        properties = new NotificationRedisProperties(
                "test-stream",
                "test-group",
                "test-consumer",
                Duration.ofSeconds(2),
                Duration.ofSeconds(30)
        );
        consumer = new NotificationEventConsumer(redisTemplate, processingService, properties);
    }

    @Test
    void handleRecordProcessesAndAcknowledges() {
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);

        MapRecord<String, String, String> record = MapRecord.create(
                properties.streamKey(),
                Map.of("eventId", "evt-1", "articleId", "art-1")
        ).withId(RecordId.of("100-0"));

        consumer.handleRecord(record);

        verify(processingService).processEvent(record.getValue());
        verify(streamOperations).acknowledge(properties.streamKey(), properties.consumerGroup(), RecordId.of("100-0"));
    }

    @Test
    void handleRecordDoesNotAcknowledgeWhenProcessingFails() {
        MapRecord<String, String, String> record = MapRecord.create(
                properties.streamKey(),
                Map.of("eventId", "evt-1")
        ).withId(RecordId.of("100-0"));

        doThrow(new RuntimeException("processing failed"))
                .when(processingService).processEvent(any());

        consumer.handleRecord(record);

        verify(processingService).processEvent(record.getValue());
        verify(streamOperations, never()).acknowledge(any(), any(), any(RecordId.class));
    }

    @Test
    void isSubscribedReturnsFalseWhenNotStarted() {
        assertThat(consumer.isSubscribed()).isFalse();
    }

    @Test
    void superviseSubscriptionAttemptsToStartWhenUnhealthy() {
        when(redisTemplate.hasKey(properties.streamKey())).thenReturn(true);
        // Connection factory is null in mock, so startConsumer will catch exception and leave started=false
        consumer.superviseSubscription();

        assertThat(consumer.isSubscribed()).isFalse();
    }

    @Test
    void destroyCleansUpSafely() {
        consumer.destroy();
        assertThat(consumer.isSubscribed()).isFalse();
    }
}
