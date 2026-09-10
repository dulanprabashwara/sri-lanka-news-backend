package lk.srilankannews.processing.enrichment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("news.ai.gemini.background")
public record GeminiBackgroundProperties(
        int maxConcurrency,
        Duration minimumSpacing,
        Duration rateLimitCooldown) {
}
