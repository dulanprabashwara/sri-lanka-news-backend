package lk.srilankannews.translation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.ai.multilingual")
public record TranslationProperties(
        @NotBlank String model,
        @NotBlank String promptVersion,
        @Min(100) @Max(100000) int maxInputCharacters,
        @Min(0) @Max(1000) int backfillLimit) {
}
