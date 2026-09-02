package lk.srilankannews.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.rate-limit")
public record PublicAiRateLimitProperties(
        @Min(1) @Max(10_000) int semanticSearchPerMinute,
        @Min(1) @Max(10_000) int askStoryPerMinute,
        @Min(100) @Max(100_000) int maxClients) {
}
