package lk.srilankannews.notifications;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("news.notifications.redis")
public record NotificationRedisProperties(
        String streamKey,
        String consumerGroup,
        String consumerName,
        Duration pollTimeout,
        Duration superviseInterval) {

    @ConstructorBinding
    public NotificationRedisProperties(
            @DefaultValue("notification-events") String streamKey,
            @DefaultValue("notification-processing") String consumerGroup,
            @DefaultValue("worker-1") String consumerName,
            @DefaultValue("100ms") Duration pollTimeout,
            @DefaultValue("30s") Duration superviseInterval) {
        this.streamKey = streamKey != null && !streamKey.isBlank() ? streamKey : "notification-events";
        this.consumerGroup = consumerGroup != null && !consumerGroup.isBlank() ? consumerGroup : "notification-processing";
        this.consumerName = consumerName != null && !consumerName.isBlank() ? consumerName : "worker-1";
        this.pollTimeout = pollTimeout != null ? pollTimeout : Duration.ofMillis(100);
        this.superviseInterval = superviseInterval != null ? superviseInterval : Duration.ofSeconds(30);
    }

    public NotificationRedisProperties() {
        this("notification-events", "notification-processing", "worker-1", Duration.ofMillis(100), Duration.ofSeconds(30));
    }
}
