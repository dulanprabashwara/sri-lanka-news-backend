package lk.srilankannews.retention;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroup;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroups;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoStream;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisReadonlyReconciliationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisReadonlyReconciliationRunner.class);

    @Test
    void executeReadonlyReconciliationAudit() {
        String redisUrlStr = System.getenv("REDIS_URL");
        if (redisUrlStr == null || redisUrlStr.isBlank()) {
            LOGGER.info("REDIS_URL not set. Skipping live read-only reconciliation.");
            return;
        }

        URI uri = URI.create(redisUrlStr);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 6379;
        String userInfo = uri.getUserInfo();
        String password = null;
        if (userInfo != null && userInfo.contains(":")) {
            password = userInfo.split(":", 2)[1];
        } else if (userInfo != null) {
            password = userInfo;
        }

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isBlank()) {
            redisConfig.setPassword(RedisPassword.of(password));
        }

        boolean useSsl = "rediss".equalsIgnoreCase(uri.getScheme());
        LettuceClientConfiguration clientConfig = useSsl
                ? LettuceClientConfiguration.builder().useSsl().build()
                : LettuceClientConfiguration.builder().build();

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisConfig, clientConfig);
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();

        try {
            LOGGER.info("=== RECONCILIATION AUDIT START ===");
            auditStream(redis, "article-discovered");
            auditStream(redis, "notification-events");
            auditStream(redis, "article-discovered-dlq");
            LOGGER.info("=== RECONCILIATION AUDIT END ===");
        } finally {
            connectionFactory.destroy();
        }
    }

    private void auditStream(StringRedisTemplate redis, String streamKey) {
        LOGGER.info("--- Stream Audit: {} ---", streamKey);

        Boolean hasKey = redis.hasKey(streamKey);
        if (!Boolean.TRUE.equals(hasKey)) {
            LOGGER.info("Stream key exists: NO");
            return;
        }

        Long xlen = redis.opsForStream().size(streamKey);
        LOGGER.info("XLEN: {}", xlen);

        if (xlen == null || xlen == 0) {
            LOGGER.info("Stream is empty (XLEN = 0)");
            return;
        }

        try {
            XInfoStream info = redis.opsForStream().info(streamKey);
            if (info != null) {
                LOGGER.info("XINFO STREAM info object: {}", info.toString());
            }
        } catch (Exception e) {
            LOGGER.warn("XINFO STREAM error: {}", e.getMessage());
        }

        String xrangeFirstId = null;
        try {
            List<MapRecord<String, Object, Object>> firstList = redis.opsForStream().range(streamKey, Range.unbounded());
            if (firstList != null && !firstList.isEmpty()) {
                xrangeFirstId = firstList.get(0).getId().getValue();
            }
        } catch (Exception e) {
            LOGGER.warn("XRANGE error: {}", e.getMessage());
        }

        String xrevrangeLastId = null;
        try {
            List<MapRecord<String, Object, Object>> lastList = redis.opsForStream().reverseRange(streamKey, Range.unbounded());
            if (lastList != null && !lastList.isEmpty()) {
                xrevrangeLastId = lastList.get(0).getId().getValue();
            }
        } catch (Exception e) {
            LOGGER.warn("XREVRANGE error: {}", e.getMessage());
        }

        LOGGER.info("XRANGE First Entry ID: {}", xrangeFirstId);
        LOGGER.info("XREVRANGE Last Entry ID: {}", xrevrangeLastId);

        // Consumer Groups Audit
        try {
            XInfoGroups groups = redis.opsForStream().groups(streamKey);
            if (groups != null && !groups.isEmpty()) {
                for (XInfoGroup g : groups) {
                    String gName = g.groupName();
                    String lastDelivered = g.lastDeliveredId();

                    PendingMessagesSummary pendingSummary = redis.opsForStream().pending(streamKey, gName);
                    long pendingCount = pendingSummary != null ? pendingSummary.getTotalPendingMessages() : 0;

                    String oldestPendingId = null;
                    if (pendingCount > 0 && pendingSummary != null && pendingSummary.minRecordId() != null) {
                        oldestPendingId = pendingSummary.minRecordId().getValue();
                    }

                    LOGGER.info("Group: {}, lastDeliveredId: {}, pendingCount: {}, oldestPendingId: {}",
                            gName, lastDelivered, pendingCount, oldestPendingId);

                    // Check oldest pending body existence
                    if (oldestPendingId != null) {
                        List<MapRecord<String, Object, Object>> pendingRecord = redis.opsForStream().range(streamKey, Range.closed(oldestPendingId, oldestPendingId));
                        boolean exists = pendingRecord != null && !pendingRecord.isEmpty();
                        LOGGER.info("OLDEST PENDING BODY EXISTS for group {}: {}", gName, exists ? "YES" : "NO");
                    }
                }
            } else {
                LOGGER.info("Consumer groups: NONE");
            }
        } catch (Exception e) {
            LOGGER.warn("Consumer group audit error: {}", e.getMessage());
        }
    }
}
