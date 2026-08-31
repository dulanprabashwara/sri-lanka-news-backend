package lk.srilankannews.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GeminiPropertiesTest {

    @Test
    void doesNotExposeApiKeyThroughStringRepresentation() {
        GeminiProperties properties = new GeminiProperties(
                "super-secret-key", "gemini-test", "v1", Duration.ofSeconds(5), 30000);

        assertThat(properties.toString())
                .doesNotContain("super-secret-key")
                .contains("apiKey=***");
    }
}
