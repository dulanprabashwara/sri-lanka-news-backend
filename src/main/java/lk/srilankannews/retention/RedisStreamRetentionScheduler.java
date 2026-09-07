package lk.srilankannews.retention;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "news.retention.redis.schedule-enabled", havingValue = "true")
public class RedisStreamRetentionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisStreamRetentionScheduler.class);
    public static final String LEASE_KEY = "news:retention:redis:lease";
    public static final Duration LEASE_TTL = Duration.ofMinutes(10);

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final RedisStreamRetentionService retentionService;
    private final StringRedisTemplate redis;
    private final String instanceId;

    public RedisStreamRetentionScheduler(
            RedisStreamRetentionService retentionService,
            StringRedisTemplate redis) {
        this.retentionService = retentionService;
        this.redis = redis;
        this.instanceId = UUID.randomUUID().toString();
    }

    @Scheduled(cron = "${news.retention.redis.schedule-cron:0 0 */6 * * *}")
    public void runScheduledRetention() {
        boolean acquired = acquireLease();
        if (!acquired) {
            LOGGER.info("redis_retention_schedule_skipped lease_not_acquired instanceId={}", instanceId);
            return;
        }

        try {
            LOGGER.info("redis_retention_schedule_started instanceId={}", instanceId);
            retentionService.processStream(RedisStreamRetentionService.DEFAULT_ARTICLE_STREAM, false);
            retentionService.processStream(RedisStreamRetentionService.DEFAULT_NOTIFICATION_STREAM, false);
            LOGGER.info("redis_retention_schedule_completed instanceId={}", instanceId);
        } catch (Exception e) {
            LOGGER.error("redis_retention_schedule_error instanceId={} error={}", instanceId, e.getMessage(), e);
        } finally {
            releaseLease();
        }
    }

    public boolean acquireLease() {
        try {
            Boolean success = redis.opsForValue().setIfAbsent(LEASE_KEY, instanceId, LEASE_TTL);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            LOGGER.warn("Failed to acquire Redis retention lease: {}", e.getMessage());
            return false;
        }
    }

    public void releaseLease() {
        try {
            redis.execute(RELEASE_SCRIPT, Collections.singletonList(LEASE_KEY), instanceId);
        } catch (Exception e) {
            LOGGER.warn("Failed to release Redis retention lease: {}", e.getMessage());
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}
