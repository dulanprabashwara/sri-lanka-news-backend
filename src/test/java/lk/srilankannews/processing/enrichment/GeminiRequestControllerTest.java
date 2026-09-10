package lk.srilankannews.processing.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import lk.srilankannews.ai.AiProviderException;
import org.junit.jupiter.api.Test;

class GeminiRequestControllerTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-10T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void backgroundConcurrencyIsBoundedWithoutQueuingInteractiveRequests() {
        GeminiRequestController controller = controller(Duration.ZERO);
        GeminiRequestController.Permit permit = controller.tryAcquireBackground().orElseThrow();

        assertThat(controller.tryAcquireBackground()).isEmpty();
        assertThatCode(controller::requireInteractiveAvailability).doesNotThrowAnyException();

        permit.close();
        assertThat(controller.tryAcquireBackground()).isPresent();
    }

    @Test
    void knownRateLimitCooldownDefersBackgroundAndFailsInteractiveFast() {
        GeminiRequestController controller = controller(Duration.ZERO);
        controller.recordRateLimit();

        assertThat(controller.tryAcquireBackground()).isEmpty();
        assertThatThrownBy(controller::requireInteractiveAvailability)
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).kind())
                .isEqualTo(AiProviderException.Kind.RATE_LIMIT);
    }

    @Test
    void minimumSpacingDefersAnotherBackgroundRequest() {
        GeminiRequestController controller = controller(Duration.ofSeconds(2));
        controller.tryAcquireBackground().orElseThrow().close();

        assertThat(controller.tryAcquireBackground()).isEmpty();
    }

    private GeminiRequestController controller(Duration spacing) {
        return new GeminiRequestController(
                new GeminiBackgroundProperties(1, spacing, Duration.ofMinutes(1)), CLOCK);
    }
}
