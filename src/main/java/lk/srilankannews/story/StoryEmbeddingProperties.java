package lk.srilankannews.story;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.story.embedding")
public record StoryEmbeddingProperties(
        @NotBlank String model,
        @Min(1) @Max(3072) int dimensions,
        @NotBlank String inputVersion,
        @Min(256) int maxInputCharacters,
        @DecimalMin("0.0") @DecimalMax("1.0") double semanticThreshold,
        @DecimalMin("0.0") @DecimalMax("1.0") double crossLanguageSemanticThreshold,
        @Min(0) int backfillLimit) {
}
