package lk.srilankannews.translation;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.ai.multilingual.azure")
public record AzureTranslatorProperties(
        String key,
        String endpoint,
        String region,
        Duration timeout,
        @Min(1) int maxInputCharacters) {

    public boolean configured() {
        return key != null && !key.isBlank() && endpoint != null && !endpoint.isBlank();
    }
}
