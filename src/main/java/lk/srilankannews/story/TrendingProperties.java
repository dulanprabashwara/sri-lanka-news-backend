package lk.srilankannews.story;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.story.trending")
public record TrendingProperties(
        @Min(6) @Max(336) int windowHours,
        @DecimalMin(value = "0.0", inclusive = false) double recencyHalfLifeHours,
        @DecimalMin(value = "0.0", inclusive = false) double sourceNormalization,
        @DecimalMin(value = "0.0", inclusive = false) double reportNormalization,
        @Min(50) @Max(5000) int maxCandidates) {
}
