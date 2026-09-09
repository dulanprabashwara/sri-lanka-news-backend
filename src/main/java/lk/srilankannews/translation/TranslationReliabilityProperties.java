package lk.srilankannews.translation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.ai.multilingual.reliability")
public record TranslationReliabilityProperties(
        @Min(1) @Max(5) int geminiMaxAttempts,
        Duration initialBackoff) {
}
