package lk.srilankannews.ai;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import java.util.List;
import lk.srilankannews.story.StoryEmbeddingProperties;

public class GeminiEmbeddingProvider implements EmbeddingProvider {
    private final EmbeddingRequestExecutor requestExecutor;
    private final StoryEmbeddingProperties properties;

    public GeminiEmbeddingProvider(Client client, StoryEmbeddingProperties properties) {
        this(client.models::embedContent, properties);
    }

    GeminiEmbeddingProvider(
            EmbeddingRequestExecutor requestExecutor,
            StoryEmbeddingProperties properties) {
        this.requestExecutor = requestExecutor;
        this.properties = properties;
    }

    @Override
    public List<Double> embed(String input) {
        try {
            EmbedContentConfig config = EmbedContentConfig.builder()
                    .outputDimensionality(properties.dimensions())
                    .build();
            EmbedContentResponse response = requestExecutor.embed(
                    properties.model(), input, config);
            List<ContentEmbedding> embeddings = response.embeddings().orElse(List.of());
            if (embeddings.size() != 1) {
                throw invalidResponse("Gemini returned no single embedding", "EMPTY_EMBEDDING");
            }
            List<Float> values = embeddings.get(0).values().orElse(List.of());
            if (values.isEmpty()) {
                throw invalidResponse("Gemini returned an empty embedding", "EMPTY_EMBEDDING");
            }
            return values.stream().map(Float::doubleValue).toList();
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw GeminiFailureMapper.map(exception, properties.model());
        }
    }

    private AiProviderException invalidResponse(String message, String code) {
        return new AiProviderException(
                AiProviderException.Kind.INVALID_RESPONSE,
                message,
                null,
                code,
                message,
                properties.model(),
                null);
    }

    @FunctionalInterface
    interface EmbeddingRequestExecutor {
        EmbedContentResponse embed(
                String model, String input, EmbedContentConfig config);
    }
}
