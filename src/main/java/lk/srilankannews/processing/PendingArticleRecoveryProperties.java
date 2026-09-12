package lk.srilankannews.processing;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("news.processing.pending-recovery")
public record PendingArticleRecoveryProperties(
        boolean enabled,
        Duration fixedDelay,
        Duration staleAfter,
        int batchSize) {

    @ConstructorBinding
    public PendingArticleRecoveryProperties(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("5m") Duration fixedDelay,
            @DefaultValue("5m") Duration staleAfter,
            @DefaultValue("50") int batchSize) {
        this.enabled = enabled;
        this.fixedDelay = fixedDelay == null ? Duration.ofMinutes(5) : fixedDelay;
        this.staleAfter = staleAfter == null ? Duration.ofMinutes(5) : staleAfter;
        this.batchSize = batchSize <= 0 ? 50 : batchSize;
    }

    public PendingArticleRecoveryProperties() {
        this(true, Duration.ofMinutes(5), Duration.ofMinutes(5), 50);
    }
}
