package lk.srilankannews.article.search;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.search.semantic")
public record SemanticSearchProperties(
        @NotBlank String vectorIndex,
        @DecimalMin("0.0") @DecimalMax("1.0") double minScore,
        @Min(1) @Max(1000) int maxWindow) {
}
