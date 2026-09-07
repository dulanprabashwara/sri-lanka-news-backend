package lk.srilankannews.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class RedisStreamRetentionSchedulerTest {

    @Mock
    private RedisStreamRetentionService retentionService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisStreamRetentionScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        scheduler = new RedisStreamRetentionScheduler(retentionService, redis);
    }

    @Test
    void acquireAndReleaseLease() {
        when(valueOps.setIfAbsent(eq(RedisStreamRetentionScheduler.LEASE_KEY), eq(scheduler.getInstanceId()), any(Duration.class)))
                .thenReturn(true);

        assertThat(scheduler.acquireLease()).isTrue();
        scheduler.releaseLease();

        verify(redis).execute(any(RedisScript.class), eq(List.of(RedisStreamRetentionScheduler.LEASE_KEY)), eq(scheduler.getInstanceId()));
    }

    @Test
    void scheduledRunAcquiresLeaseAndProcessesStreams() {
        when(valueOps.setIfAbsent(eq(RedisStreamRetentionScheduler.LEASE_KEY), eq(scheduler.getInstanceId()), any(Duration.class)))
                .thenReturn(true);

        scheduler.runScheduledRetention();

        verify(retentionService).processStream("article-discovered", false);
        verify(retentionService).processStream("notification-events", false);
        verify(redis).execute(any(RedisScript.class), eq(List.of(RedisStreamRetentionScheduler.LEASE_KEY)), eq(scheduler.getInstanceId()));
    }

    @Test
    void skipsScheduledRunIfLeaseUnacquired() {
        when(valueOps.setIfAbsent(eq(RedisStreamRetentionScheduler.LEASE_KEY), eq(scheduler.getInstanceId()), any(Duration.class)))
                .thenReturn(false);

        scheduler.runScheduledRetention();

        verify(retentionService, never()).processStream(anyString(), eq(false));
    }
}
