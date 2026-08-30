package lk.srilankannews.processing;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("news.processing.redis")
public record RedisProcessingProperties(
        boolean enabled,
        String streamKey,
        String deadLetterStreamKey,
        String consumerGroup,
        String consumerName,
        int maxAttempts,
        Duration pollTimeout) {
}
