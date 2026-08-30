package lk.srilankannews.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IngestionApiKeyAuthenticator {

    public static final String HEADER_NAME = "X-Ingestion-API-Key";

    private final byte[] expectedKey;

    public IngestionApiKeyAuthenticator(@Value("${ingestion.api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("INGESTION_API_KEY must be configured.");
        }
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    public void authenticate(String candidate) {
        byte[] candidateBytes = candidate == null
                ? new byte[0]
                : candidate.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKey, candidateBytes)) {
            throw new InvalidIngestionApiKeyException();
        }
    }
}
