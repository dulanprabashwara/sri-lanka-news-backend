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

    private RedisProcessingProperties properties() {
        return new RedisProcessingProperties(
                true, "article-discovered", "article-discovered-dlq",
                "article-processing", "test-consumer", 3, Duration.ofSeconds(2));
    }
}
