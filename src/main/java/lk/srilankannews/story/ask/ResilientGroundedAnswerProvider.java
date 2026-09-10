package lk.srilankannews.story.ask;

import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.openrouter.OpenRouterProperties;
import lk.srilankannews.processing.enrichment.GeminiRequestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResilientGroundedAnswerProvider implements GroundedAnswerProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientGroundedAnswerProvider.class);

    private final GroundedAnswerProvider primaryProvider;
    private final GroundedAnswerProvider fallbackProvider;
    private final OpenRouterProperties openRouterProperties;
    private final GeminiRequestController geminiRequestController;

    public ResilientGroundedAnswerProvider(
            GroundedAnswerProvider primaryProvider,
            GroundedAnswerProvider fallbackProvider,
            OpenRouterProperties openRouterProperties,
            GeminiRequestController geminiRequestController) {
        this.primaryProvider = primaryProvider;
        this.fallbackProvider = fallbackProvider;
        this.openRouterProperties = openRouterProperties;
        this.geminiRequestController = geminiRequestController;
    }

    @Override
    public boolean hasFallback() {
        return fallbackProvider != null && openRouterProperties != null && openRouterProperties.configured();
    }

    @Override
    public GroundedAnswerResult answer(GroundedAnswerInput input) {
        boolean canFallback = hasFallback();

        if (canFallback && geminiRequestController != null && geminiRequestController.isCoolingDown()) {
            LOGGER.info("gemini_cooling_down_routing_to_openrouter storyTitle={}", input.storyTitle());
            return fallbackProvider.answer(input);
        }

        try {
            return primaryProvider.answer(input);
        } catch (AiProviderException exception) {
            if (exception.kind() == AiProviderException.Kind.RATE_LIMIT && geminiRequestController != null) {
                geminiRequestController.recordRateLimit(exception.retryAfter());
            }

            if (!canFallback || !retryable(exception)) {
                throw exception;
            }

            LOGGER.warn(
                    "ask_story_primary_failed_trying_openrouter category={} httpStatus={} providerCode={}",
                    exception.kind(), exception.httpStatus(), exception.providerCode());
            return fallbackProvider.answer(input);
        }
    }

    private boolean retryable(AiProviderException exception) {
        return exception.kind() == AiProviderException.Kind.RATE_LIMIT
                || exception.kind() == AiProviderException.Kind.TIMEOUT_NETWORK
                || exception.kind() == AiProviderException.Kind.PROVIDER_5XX;
    }
}
