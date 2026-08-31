package lk.srilankannews.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

class GeminiFailureMapperTest {

    @Test
    void preservesSanitizedApiFailureDetailsAndOriginalCause() {
        ClientException providerFailure = new ClientException(
                401,
                "UNAUTHENTICATED",
                "API key invalid at https://generativelanguage.googleapis.com/v1/models"
                        + "?key=secret-value");

        AiProviderException mapped =
                GeminiFailureMapper.map(providerFailure, "gemini-2.5-flash");

        assertThat(mapped.kind()).isEqualTo(AiProviderException.Kind.AUTHENTICATION);
        assertThat(mapped.httpStatus()).isEqualTo(401);
        assertThat(mapped.providerCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(mapped.providerMessage())
                .contains("key=***")
                .doesNotContain("secret-value");
        assertThat(mapped.model()).isEqualTo("gemini-2.5-flash");
        assertThat(mapped.getCause()).isSameAs(providerFailure);
    }

    @Test
    void categorizesCommonGeminiApiFailures() {
        assertThat(GeminiFailureMapper.classify(403, "PERMISSION_DENIED", "denied"))
                .isEqualTo(AiProviderException.Kind.PERMISSION);
        assertThat(GeminiFailureMapper.classify(429, "RESOURCE_EXHAUSTED", "quota exceeded"))
                .isEqualTo(AiProviderException.Kind.RATE_LIMIT);
        assertThat(GeminiFailureMapper.classify(404, "NOT_FOUND", "model not found"))
                .isEqualTo(AiProviderException.Kind.MODEL_NOT_FOUND);
        assertThat(GeminiFailureMapper.classify(400, "INVALID_ARGUMENT", "schema invalid"))
                .isEqualTo(AiProviderException.Kind.INVALID_REQUEST);
        assertThat(GeminiFailureMapper.classify(503, "UNAVAILABLE", "provider unavailable"))
                .isEqualTo(AiProviderException.Kind.PROVIDER_5XX);
    }

    @Test
    void categorizesNetworkTimeoutAndPreservesCauseWithoutItsMessage() {
        GenAiIOException providerFailure =
                new GenAiIOException("request failed", new SocketTimeoutException("timed out"));

        AiProviderException mapped =
                GeminiFailureMapper.map(providerFailure, "gemini-2.5-flash");

        assertThat(mapped.kind()).isEqualTo(AiProviderException.Kind.TIMEOUT_NETWORK);
        assertThat(mapped.providerMessage()).isEqualTo("Gemini network request failed");
        assertThat(mapped.getCause()).isSameAs(providerFailure);
    }
}
