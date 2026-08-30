package lk.srilankannews.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IngestionApiKeyAuthenticatorTest {

    private final IngestionApiKeyAuthenticator authenticator =
            new IngestionApiKeyAuthenticator("test-secret-value");

    @Test
    void acceptsMatchingKey() {
        assertThatCode(() -> authenticator.authenticate("test-secret-value"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrInvalidKey() {
        assertThatThrownBy(() -> authenticator.authenticate(null))
                .isInstanceOf(InvalidIngestionApiKeyException.class);
        assertThatThrownBy(() -> authenticator.authenticate("wrong-secret"))
                .isInstanceOf(InvalidIngestionApiKeyException.class);
    }

    @Test
    void rejectsBlankConfiguredKey() {
        assertThatThrownBy(() -> new IngestionApiKeyAuthenticator(" "))
                .isInstanceOf(IllegalStateException.class);
    }
}
