package lk.srilankannews.processing.enrichment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("news.ai.enrichment.retry")
public record EnrichmentRetryProperties(
        boolean enabled,
        int batchSize,
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        Duration leaseDuration) {
}
