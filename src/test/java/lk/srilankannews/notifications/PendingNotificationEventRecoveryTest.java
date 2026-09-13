package lk.srilankannews.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class PendingNotificationEventRecoveryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @Mock
    private NotificationEventConsumer consumer;

    private NotificationRedisProperties redisProperties;
    private PendingNotificationRecoveryProperties recoveryProperties;
    private PendingNotificationEventRecovery recovery;

    @BeforeEach
    void setUp() {
        redisProperties = new NotificationRedisProperties(
                "test-stream",
                "test-group",
                "test-consumer",
                Duration.ofSeconds(2),
                Duration.ofSeconds(30)
        );
        recoveryProperties = new PendingNotificationRecoveryProperties(
                true,
                Duration.ofMinutes(1),
                Duration.ofMinutes(2),
                50
        );
        recovery = new PendingNotificationEventRecovery(
                redisTemplate,
                consumer,
                redisProperties,
                recoveryProperties
        );
    }

    @Test
    void recoverStalePendingEventsDoesNothingWhenDisabled() {
        PendingNotificationRecoveryProperties disabledProps = new PendingNotificationRecoveryProperties(
                false, Duration.ofMinutes(1), Duration.ofMinutes(2), 50);
        PendingNotificationEventRecovery disabledRecovery = new PendingNotificationEventRecovery(
                redisTemplate, consumer, redisProperties, disabledProps);

        int result = disabledRecovery.recoverStalePendingEvents();

        assertThat(result).isZero();
        verify(redisTemplate, never()).hasKey(any());
    }

    @Test
    void recoverStalePendingEventsDoesNothingWhenStreamDoesNotExist() {
        when(redisTemplate.hasKey(redisProperties.streamKey())).thenReturn(false);

        int result = recovery.recoverStalePendingEvents();

        assertThat(result).isZero();
        verify(redisTemplate, never()).opsForStream();
    }

    @Test
    void recoverStalePendingEventsDoesNothingWhenPendingMessagesEmpty() {
        when(redisTemplate.hasKey(redisProperties.streamKey())).thenReturn(true);
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
        PendingMessages pending = mock(PendingMessages.class);
        when(pending.isEmpty()).thenReturn(true);
        when(streamOperations.pending(eq(redisProperties.streamKey()), eq(redisProperties.consumerGroup()), any(Range.class), eq(50L)))
                .thenReturn(pending);

        int result = recovery.recoverStalePendingEvents();

        assertThat(result).isZero();
        verify(streamOperations, never()).claim(any(), any(), any(), any(Duration.class), any(RecordId[].class));
    }

    @Test
    void recoverStalePendingEventsSkipsNonStaleMessages() {
        when(redisTemplate.hasKey(redisProperties.streamKey())).thenReturn(true);
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);

        PendingMessage freshMsg = mock(PendingMessage.class);
        when(freshMsg.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofSeconds(30)); // less than 2m

        PendingMessages pending = mock(PendingMessages.class);
        when(pending.isEmpty()).thenReturn(false);
        when(pending.iterator()).thenReturn(List.of(freshMsg).iterator());

        when(streamOperations.pending(eq(redisProperties.streamKey()), eq(redisProperties.consumerGroup()), any(Range.class), eq(50L)))
                .thenReturn(pending);

        int result = recovery.recoverStalePendingEvents();

        assertThat(result).isZero();
        verify(streamOperations, never()).claim(any(), any(), any(), any(Duration.class), any(RecordId[].class));
    }

    @Test
    void recoverStalePendingEventsClaimsAndProcessesStaleMessages() {
        when(redisTemplate.hasKey(redisProperties.streamKey())).thenReturn(true);
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);

        RecordId recordId = RecordId.of("123-0");
        PendingMessage staleMsg = mock(PendingMessage.class);
        when(staleMsg.getId()).thenReturn(recordId);
        when(staleMsg.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofMinutes(5)); // greater than 2m

        PendingMessages pending = mock(PendingMessages.class);
        when(pending.isEmpty()).thenReturn(false);
        when(pending.iterator()).thenReturn(List.of(staleMsg).iterator());

        when(streamOperations.pending(eq(redisProperties.streamKey()), eq(redisProperties.consumerGroup()), any(Range.class), eq(50L)))
                .thenReturn(pending);

        MapRecord<String, String, String> record = MapRecord.create(
                redisProperties.streamKey(),
                Map.of("eventId", "evt-1")
        ).withId(recordId);

        when(streamOperations.claim(
                eq(redisProperties.streamKey()),
                eq(redisProperties.consumerGroup()),
                eq(redisProperties.consumerName()),
                eq(recoveryProperties.staleAfter()),
                eq(new RecordId[]{recordId})
        )).thenReturn((List) List.of(record));

        int result = recovery.recoverStalePendingEvents();

        assertThat(result).isEqualTo(1);
        verify(consumer).handleRecord(record);
    }

    @Test
    void recoverStalePendingEventsHandlesExceptionGracefully() {
        when(redisTemplate.hasKey(redisProperties.streamKey())).thenThrow(new RuntimeException("Redis down"));

        int result = recovery.recoverStalePendingEvents();

        assertThat(result).isZero();
    }

    @Test
    void runInvokesRecovery() {
        when(redisTemplate.hasKey(redisProperties.streamKey())).thenReturn(false);

        recovery.run(null);

        verify(redisTemplate).hasKey(redisProperties.streamKey());
    }

    @Test
    void recoverScheduledHonorsDisabledProperty() {
        PendingNotificationRecoveryProperties disabledProps = new PendingNotificationRecoveryProperties(
                false, Duration.ofMinutes(1), Duration.ofMinutes(2), 50);
        PendingNotificationEventRecovery disabledRecovery = new PendingNotificationEventRecovery(
                redisTemplate, consumer, redisProperties, disabledProps);

        int result = disabledRecovery.recoverScheduled();

        assertThat(result).isZero();
        verify(redisTemplate, never()).hasKey(any());
    }
}

