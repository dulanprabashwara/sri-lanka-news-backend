package lk.srilankannews.notifications;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("news.notifications.pending-recovery")
public record PendingNotificationRecoveryProperties(
        boolean enabled,
        Duration fixedDelay,
        Duration staleAfter,
        int batchSize) {

    @ConstructorBinding
    public PendingNotificationRecoveryProperties(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("1m") Duration fixedDelay,
            @DefaultValue("2m") Duration staleAfter,
            @DefaultValue("50") int batchSize) {
        this.enabled = enabled;
        this.fixedDelay = fixedDelay == null ? Duration.ofMinutes(1) : fixedDelay;
        this.staleAfter = staleAfter == null ? Duration.ofMinutes(2) : staleAfter;
        this.batchSize = batchSize <= 0 ? 50 : batchSize;
    }

    public PendingNotificationRecoveryProperties() {
        this(true, Duration.ofMinutes(1), Duration.ofMinutes(2), 50);
    }
}
