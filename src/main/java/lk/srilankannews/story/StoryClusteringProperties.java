package lk.srilankannews.story;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.story.clustering")
public record StoryClusteringProperties(
        @NotNull Duration window,
        @DecimalMin("0.0") @DecimalMax("1.0") double threshold,
        @Min(1) int candidateLimit,
        @Min(0) int backfillLimit) {

    public StoryClusteringProperties {
        if (window != null && (window.isZero() || window.isNegative())) {
            throw new IllegalArgumentException("Story clustering window must be positive");
        }
    }
}
