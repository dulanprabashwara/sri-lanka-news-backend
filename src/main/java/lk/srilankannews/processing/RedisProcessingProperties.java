package lk.srilankannews.processing;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("news.processing.redis")
public record RedisProcessingProperties(
        boolean enabled,
        String streamKey,
        String deadLetterStreamKey,
        String consumerGroup,
        String consumerName,
        int maxAttempts,
        Duration pollTimeout,
        Duration superviseInterval) {

    @ConstructorBinding
    public RedisProcessingProperties(
            boolean enabled,
            String streamKey,
            String deadLetterStreamKey,
            String consumerGroup,
            String consumerName,
            int maxAttempts,
            Duration pollTimeout,
            @DefaultValue("30s") Duration superviseInterval) {
        this.enabled = enabled;
        this.streamKey = streamKey;
        this.deadLetterStreamKey = deadLetterStreamKey;
        this.consumerGroup = consumerGroup;
        this.consumerName = consumerName;
        this.maxAttempts = maxAttempts;
        this.pollTimeout = pollTimeout;
        this.superviseInterval = superviseInterval == null ? Duration.ofSeconds(30) : superviseInterval;
    }

    public RedisProcessingProperties(
            boolean enabled,
            String streamKey,
            String deadLetterStreamKey,
            String consumerGroup,
            String consumerName,
            int maxAttempts,
            Duration pollTimeout) {
        this(enabled, streamKey, deadLetterStreamKey, consumerGroup, consumerName, maxAttempts, pollTimeout, Duration.ofSeconds(30));
    }
}
