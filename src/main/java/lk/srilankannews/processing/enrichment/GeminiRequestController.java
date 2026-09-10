package lk.srilankannews.processing.enrichment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import lk.srilankannews.ai.AiProviderException;
import org.springframework.stereotype.Component;

@Component
public class GeminiRequestController {
    private final GeminiBackgroundProperties properties;
    private final Clock clock;
    private final Semaphore backgroundPermits;
    private final AtomicReference<Instant> cooldownUntil =
            new AtomicReference<>(Instant.EPOCH);
    private Instant nextBackgroundRequestAt = Instant.EPOCH;

    public GeminiRequestController(GeminiBackgroundProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.backgroundPermits = new Semaphore(Math.max(1, properties.maxConcurrency()));
    }

    public synchronized Optional<Permit> tryAcquireBackground() {
        Instant now = clock.instant();
        if (now.isBefore(cooldownUntil.get()) || now.isBefore(nextBackgroundRequestAt)
                || !backgroundPermits.tryAcquire()) {
            return Optional.empty();
        }
        nextBackgroundRequestAt = now.plus(properties.minimumSpacing());
        return Optional.of(new Permit(backgroundPermits));
    }

    public void recordRateLimit() {
        recordRateLimit(null);
    }

    public void recordRateLimit(Duration cooldown) {
        Duration duration = cooldown != null && !cooldown.isNegative() && !cooldown.isZero()
                ? cooldown
                : properties.rateLimitCooldown();
        Instant candidate = clock.instant().plus(duration);
        cooldownUntil.accumulateAndGet(candidate,
                (current, next) -> current.isAfter(next) ? current : next);
    }

    public void requireInteractiveAvailability() {
        if (clock.instant().isBefore(cooldownUntil.get())) {
            throw new AiProviderException(
                    AiProviderException.Kind.RATE_LIMIT,
                    "Gemini is temporarily cooling down after a provider rate limit");
        }
    }

    public boolean isCoolingDown() {
        return clock.instant().isBefore(cooldownUntil.get());
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean closed;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                semaphore.release();
            }
        }
    }
}
