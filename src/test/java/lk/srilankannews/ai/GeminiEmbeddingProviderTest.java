package lk.srilankannews.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lk.srilankannews.story.StoryEmbeddingProperties;
import org.junit.jupiter.api.Test;

class GeminiEmbeddingProviderTest {

    @Test
    void geminiEmbeddingTwoRequestOmitsTaskTypeAndPreservesProviderVisibleInput() {
        StoryEmbeddingProperties properties = new StoryEmbeddingProperties(
                "gemini-embedding-2", 3, "story-semantic-v2", 1000,
                0.82, 0.90, 50);
        AtomicReference<String> requestedModel = new AtomicReference<>();
        AtomicReference<String> requestedInput = new AtomicReference<>();
        AtomicReference<EmbedContentConfig> requestedConfig = new AtomicReference<>();
        EmbedContentResponse response = EmbedContentResponse.builder()
                .embeddings(ContentEmbedding.builder().values(1.0f, 2.0f, 3.0f))
                .build();
        GeminiEmbeddingProvider provider = new GeminiEmbeddingProvider(
                (model, input, config) -> {
                    requestedModel.set(model);
                    requestedInput.set(input);
                    requestedConfig.set(config);
                    return response;
                },
                properties);
        String input = "task: sentence similarity | query: deterministic content";

        assertThat(provider.embed(input)).containsExactly(1.0, 2.0, 3.0);
        assertThat(requestedModel).hasValue("gemini-embedding-2");
        assertThat(requestedInput).hasValue(input);
        assertThat(requestedConfig.get().taskType()).isEmpty();
        assertThat(requestedConfig.get().outputDimensionality()).contains(3);
    }
}
