package lk.srilankannews.ai.openrouter;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("news.ai.openrouter")
public record OpenRouterProperties(
        String apiKey,
        String model,
        String baseUrl,
        Duration timeout) {

    public OpenRouterProperties {
        if (model == null || model.isBlank()) {
            model = "openrouter/free";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://openrouter.ai/api/v1";
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofSeconds(30);
        }
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
