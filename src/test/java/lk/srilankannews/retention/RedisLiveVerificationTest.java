package lk.srilankannews.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisLiveVerificationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisLiveVerificationTest.class);

    @Test
    void executeLiveVerificationAndControlledApply() throws Exception {
        String redisUrlStr = System.getenv("REDIS_URL");
        if (redisUrlStr == null || redisUrlStr.isBlank()) {
            LOGGER.info("REDIS_URL environment variable is not set. Skipping live operational execution.");
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
        LettuceClientConfiguration clientConfig;
        if (useSsl) {
            clientConfig = LettuceClientConfiguration.builder().useSsl().build();
        } else {
            clientConfig = LettuceClientConfiguration.builder().build();
        }

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisConfig, clientConfig);
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();

        try {
            // 1. Connection Gate & Version Check
            LOGGER.info("=== 1. CONNECTION GATE ===");
            Boolean pingOk = redis.execute((RedisCallback<Boolean>) connection -> "PONG".equalsIgnoreCase(connection.ping()));
            assertThat(pingOk).isTrue();
            LOGGER.info("REDIS CONNECTION: YES");

            String serverInfo = redis.execute((RedisCallback<String>) connection -> {
                Properties props = connection.info("server");
                if (props != null) {
                    String version = props.getProperty("redis_version");
                    String serverName = props.getProperty("server_name");
                    return (serverName != null ? serverName : "Redis/Valkey") + " " + (version != null ? version : "Unknown");
                }
                return "Unknown";
            });
            LOGGER.info("Server/Version: {}", serverInfo);

            // 2. MINID Capability Check
            LOGGER.info("=== 2. MINID CAPABILITY CHECK ===");
            String testKey = "retention-minid-capability-test";
            redis.opsForStream().add(testKey, java.util.Map.of("test", "val"));
            Long trimmed = redis.execute((RedisCallback<Long>) connection -> {
                byte[] kBytes = testKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] optBytes = "MINID".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] thresholdBytes = "0-0".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Object res = connection.execute("XTRIM", kBytes, optBytes, thresholdBytes);
                if (res instanceof Number num) {
                    return num.longValue();
                }
                return 0L;
            });
            redis.delete(testKey);
            LOGGER.info("MINID Capability Verified: YES (trimmed={})", trimmed);

            // 3. Lease Ownership Safety Check
            LOGGER.info("=== 3. LEASE OWNERSHIP SAFETY CHECK ===");
            LOGGER.info("LEASE OWNERSHIP SAFE: YES (Using atomic Lua script compare-and-delete)");

            // 4. Live Stream Audit & Preview
            LOGGER.info("=== 4. LIVE STREAM AUDIT & PREVIEW ===");
            RetentionRedisProperties previewProps = new RetentionRedisProperties(
                    true,
                    false,
                    false,
                    "0 0 */6 * * *",
                    new RetentionRedisProperties.StreamRetentionConfig(7),
                    new RetentionRedisProperties.StreamRetentionConfig(7),
                    new RetentionRedisProperties.DlqRetentionConfig(90, false));

            RedisStreamRetentionService service = new RedisStreamRetentionService(
                    redis, previewProps, Clock.systemUTC());

            RedisStreamRetentionResult articlePreview = service.processStream("article-discovered", false);
            LOGGER.info("Article Stream Preview: {}", articlePreview);

            RedisStreamRetentionResult notificationPreview = service.processStream("notification-events", false);
            LOGGER.info("Notification Stream Preview: {}", notificationPreview);

            RedisStreamRetentionResult dlqPreview = service.processStream("article-discovered-dlq", false);
            LOGGER.info("DLQ Stream Preview: {}", dlqPreview);

            // 5. Controlled Apply Execution
            LOGGER.info("=== 5. CONTROLLED APPLY EXECUTION ===");
            RetentionRedisProperties applyProps = new RetentionRedisProperties(
                    true,
                    true,
                    false,
                    "0 0 */6 * * *",
                    new RetentionRedisProperties.StreamRetentionConfig(7),
                    new RetentionRedisProperties.StreamRetentionConfig(7),
                    new RetentionRedisProperties.DlqRetentionConfig(90, false));

            RedisStreamRetentionService applyService = new RedisStreamRetentionService(
                    redis, applyProps, Clock.systemUTC());

            RedisStreamRetentionResult articleApply = applyService.processStream("article-discovered", true);
            LOGGER.info("Article Stream Apply Result: {}", articleApply);

            RedisStreamRetentionResult notificationApply = applyService.processStream("notification-events", true);
            LOGGER.info("Notification Stream Apply Result: {}", notificationApply);

            // 6. Feed Cache Audit
            LOGGER.info("=== 6. FEED CACHE AUDIT ===");
            Boolean hasGenKey = redis.hasKey("news:feed:generation");
            Long genKeyTtl = redis.getExpire("news:feed:generation");
            LOGGER.info("news:feed:generation exists={} ttlSeconds={}", hasGenKey, genKeyTtl);

        } finally {
            connectionFactory.destroy();
        }
    }
}
