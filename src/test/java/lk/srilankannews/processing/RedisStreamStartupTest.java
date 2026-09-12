package lk.srilankannews.processing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.TaskScheduler;

class RedisStreamStartupTest {

    @Test
    void retriesConsumerStartupAfterInitialRedisFailure() {
        RedisStreamGroupManager groupManager = mock(RedisStreamGroupManager.class);
        RedisArticleStreamListener listener = mock(RedisArticleStreamListener.class);
        @SuppressWarnings("unchecked")
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                mock(StreamMessageListenerContainer.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        RedisProcessingProperties properties = properties();
        doThrow(new IllegalStateException("Redis unavailable"))
                .doReturn(null)
                .when(groupManager).ensureConsumerGroup();
        var startup = new RedisStreamStartup(
                groupManager, properties, listener, container, scheduler);

        startup.run(null);

        verify(container, never()).start();
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(retry.capture(), any(Instant.class));

        retry.getValue().run();

        verify(container).receive(any(), any(), any());
        verify(container).start();
        verify(groupManager, org.mockito.Mockito.times(2)).ensureConsumerGroup();
    }

    @Test
    void successfulStartupDoesNotScheduleRetry() {
        RedisStreamGroupManager groupManager = mock(RedisStreamGroupManager.class);
        RedisArticleStreamListener listener = mock(RedisArticleStreamListener.class);
        @SuppressWarnings("unchecked")
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                mock(StreamMessageListenerContainer.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(groupManager.ensureConsumerGroup())
                .thenReturn(RedisStreamGroupManager.GroupInitializationResult.EXISTING);
        var startup = new RedisStreamStartup(
                groupManager, properties(), listener, container, scheduler);

        startup.run(null);

        verify(container).receive(any(), any(), any());
        verify(container).start();
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void superviseSubscriptionDoesNothingWhenActiveAndRunning() {
        RedisStreamGroupManager groupManager = mock(RedisStreamGroupManager.class);
        RedisArticleStreamListener listener = mock(RedisArticleStreamListener.class);
        @SuppressWarnings("unchecked")
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                mock(StreamMessageListenerContainer.class);
        org.springframework.data.redis.stream.Subscription subscription =
                mock(org.springframework.data.redis.stream.Subscription.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);

        when(groupManager.ensureConsumerGroup())
                .thenReturn(RedisStreamGroupManager.GroupInitializationResult.EXISTING);
        when(container.receive(any(), any(), any())).thenReturn(subscription);
        when(subscription.isActive()).thenReturn(true);
        when(container.isRunning()).thenReturn(true);

        var startup = new RedisStreamStartup(
                groupManager, properties(), listener, container, scheduler);

        startup.run(null);
        verify(container, org.mockito.Mockito.times(1)).receive(any(), any(), any());

        startup.superviseSubscription();
        verify(container, org.mockito.Mockito.times(1)).receive(any(), any(), any());
        verify(subscription, never()).cancel();
    }

    @Test
    void superviseSubscriptionRestartsWhenSubscriptionBecomesInactive() {
        RedisStreamGroupManager groupManager = mock(RedisStreamGroupManager.class);
        RedisArticleStreamListener listener = mock(RedisArticleStreamListener.class);
        @SuppressWarnings("unchecked")
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                mock(StreamMessageListenerContainer.class);
        org.springframework.data.redis.stream.Subscription subscription1 =
                mock(org.springframework.data.redis.stream.Subscription.class);
        org.springframework.data.redis.stream.Subscription subscription2 =
                mock(org.springframework.data.redis.stream.Subscription.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);

        when(groupManager.ensureConsumerGroup())
                .thenReturn(RedisStreamGroupManager.GroupInitializationResult.EXISTING);
        when(container.receive(any(), any(), any()))
                .thenReturn(subscription1)
                .thenReturn(subscription2);
        when(subscription1.isActive()).thenReturn(true);
        when(subscription2.isActive()).thenReturn(true);
        when(container.isRunning()).thenReturn(true);

        var startup = new RedisStreamStartup(
                groupManager, properties(), listener, container, scheduler);

        startup.run(null);
        verify(container, org.mockito.Mockito.times(1)).receive(any(), any(), any());

        // Subscription drops
        when(subscription1.isActive()).thenReturn(false);

        startup.superviseSubscription();

        verify(subscription1).cancel();
        verify(container).remove(subscription1);
        verify(container, org.mockito.Mockito.times(2)).receive(any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(startup.isSubscribed()).isTrue();
    }

    @Test
    void superviseSubscriptionSchedulesRetryOnFailure() {
        RedisStreamGroupManager groupManager = mock(RedisStreamGroupManager.class);
        RedisArticleStreamListener listener = mock(RedisArticleStreamListener.class);
        @SuppressWarnings("unchecked")
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                mock(StreamMessageListenerContainer.class);
        org.springframework.data.redis.stream.Subscription subscription =
                mock(org.springframework.data.redis.stream.Subscription.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);

        when(groupManager.ensureConsumerGroup())
                .thenReturn(RedisStreamGroupManager.GroupInitializationResult.EXISTING)
                .thenThrow(new IllegalStateException("Redis connection refused"));
        when(container.receive(any(), any(), any())).thenReturn(subscription);
        when(subscription.isActive()).thenReturn(true);
        when(container.isRunning()).thenReturn(true);

        var startup = new RedisStreamStartup(
                groupManager, properties(), listener, container, scheduler);

        startup.run(null);

        // Subscription drops
        when(subscription.isActive()).thenReturn(false);

        startup.superviseSubscription();

        verify(subscription).cancel();
        verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    private RedisProcessingProperties properties() {
        return new RedisProcessingProperties(
                true, "article-discovered", "article-discovered-dlq",
                "article-processing", "test-consumer", 3, Duration.ofSeconds(2));
    }
}
