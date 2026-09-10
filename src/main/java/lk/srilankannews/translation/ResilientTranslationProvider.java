package lk.srilankannews.translation;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResilientTranslationProvider implements TranslationProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientTranslationProvider.class);

    private final TranslationProvider primary;
    private final TranslationProvider fallback;
    private final TranslationReliabilityProperties properties;
    private final TranslationOutputValidator outputValidator;
    private final Sleeper sleeper;

    public ResilientTranslationProvider(
            TranslationProvider primary,
            TranslationProvider fallback,
            TranslationReliabilityProperties properties,
            TranslationOutputValidator outputValidator) {
        this(primary, fallback, properties, outputValidator,
                duration -> Thread.sleep(duration.toMillis()));
    }

    ResilientTranslationProvider(
            TranslationProvider primary,
            TranslationProvider fallback,
            TranslationReliabilityProperties properties,
            TranslationOutputValidator outputValidator,
            Sleeper sleeper) {
        this.primary = primary;
        this.fallback = fallback;
        this.properties = properties;
        this.outputValidator = outputValidator;
        this.sleeper = sleeper;
    }

    @Override
    public List<TranslatedContent> translate(TranslationInput input) {
        TranslationProviderException lastFailure = null;
        for (int attempt = 1; attempt <= properties.geminiMaxAttempts(); attempt++) {
            try {
                return validate(primary.translate(input), input);
            } catch (TranslationProviderException exception) {
                lastFailure = exception;
                if (!exception.fallbackEligible()) {
                    throw exception;
                }
                LOGGER.warn("translation_primary_failed kind={} attempt={} maxAttempts={}",
                        exception.kind(), attempt, properties.geminiMaxAttempts());
                if (attempt < properties.geminiMaxAttempts()) {
                    sleep(backoff(attempt));
                }
            }
        }
        LOGGER.warn("translation_fallback_started primaryFailureKind={}", lastFailure.kind());
        return validate(fallback.translate(input), input);
    }

    private List<TranslatedContent> validate(
            List<TranslatedContent> output, TranslationInput input) {
        Map<lk.srilankannews.common.domain.Language, TranslatedContent> validated =
                outputValidator.validate(
                        output,
                        input.targetLanguages(),
                        input.summary() != null && !input.summary().isBlank(),
                        input);
        return validated.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private Duration backoff(int attempt) {
        return properties.initialBackoff().multipliedBy(1L << (attempt - 1));
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TranslationProviderException(
                    TranslationProviderException.Kind.TIMEOUT_NETWORK,
                    "Translation retry was interrupted.", exception);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
