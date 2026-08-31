package lk.srilankannews.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.ai.gemini")
public record GeminiProperties(
        @NotBlank String apiKey,
        @NotBlank String model,
        @NotBlank String promptVersion,
        Duration timeout,
        @Min(1000) @Max(100000) int maxInputCharacters) {

    @Override
    public String toString() {
        return "GeminiProperties[apiKey=***, model=" + model
                + ", promptVersion=" + promptVersion
                + ", timeout=" + timeout
                + ", maxInputCharacters=" + maxInputCharacters + "]";
    }
}
