package lk.srilankannews.ai.openrouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import lk.srilankannews.ai.AiEntity;
import lk.srilankannews.ai.AiInput;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.AiResult;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenRouterAiProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenRouterProperties properties = new OpenRouterProperties(
            "test-key", "openrouter/free", "https://openrouter.ai/api/v1", Duration.ofSeconds(10));
    private OpenRouterClient client;
    private OpenRouterAiProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(OpenRouterClient.class);
        provider = new OpenRouterAiProvider(client, properties, objectMapper);
    }

    @Test
    void enrichesArticleSuccessfullyWithProviderProvenance() {
        String json = """
                {
                  "summary": "Cabinet approves new renewable energy framework.",
                  "category": "ECONOMY",
                  "topics": ["Energy", "Policy"],
                  "keywords": ["solar", "wind"],
                  "entities": [
                    {"name": "Ministry of Power", "type": "ORGANIZATION"}
                  ]
                }
                """;
        // Wait, "ECONOMY" is not in ArticleCategory enum, let's use "BUSINESS"
        String validJson = """
                {
                  "summary": "Cabinet approves new renewable energy framework.",
                  "category": "BUSINESS",
                  "topics": ["Energy", "Policy"],
                  "keywords": ["solar", "wind"],
                  "entities": [
                    {"name": "Ministry of Power", "type": "ORGANIZATION"}
                  ]
                }
                """;
        when(client.chatCompletion(anyString())).thenReturn(validJson);

        AiResult result = provider.enrich(new AiInput("Energy Framework", "Content here", Language.EN));

        assertThat(result.summary()).isEqualTo("Cabinet approves new renewable energy framework.");
        assertThat(result.category()).isEqualTo(ArticleCategory.BUSINESS);
        assertThat(result.topics()).containsExactly("Energy", "Policy");
        assertThat(result.keywords()).containsExactly("solar", "wind");
        assertThat(result.entities()).containsExactly(new AiEntity("Ministry of Power", "ORGANIZATION"));
        assertThat(result.provider()).isEqualTo("OPENROUTER");
        assertThat(result.model()).isEqualTo("openrouter/free");
    }

    @Test
    void handlesFencedJsonAndExtraCommentary() {
        String fenced = """
                Here is the parsed metadata:
                ```json
                {
                  "summary": "Sri Lanka cricket team secures victory.",
                  "category": "SPORTS",
                  "topics": ["Cricket"],
                  "keywords": ["victory"],
                  "entities": []
                }
                ```
                Hope this meets the requirements!
                """;
        when(client.chatCompletion(anyString())).thenReturn(fenced);

        AiResult result = provider.enrich(new AiInput("Match Victory", "Cricket content", Language.EN));

        assertThat(result.summary()).isEqualTo("Sri Lanka cricket team secures victory.");
        assertThat(result.category()).isEqualTo(ArticleCategory.SPORTS);
        assertThat(result.provider()).isEqualTo("OPENROUTER");
    }

    @Test
    void rejectsInvalidCategory() {
        String invalidCategoryJson = """
                {
                  "summary": "Some summary",
                  "category": "NON_EXISTENT_CATEGORY",
                  "topics": [],
                  "keywords": [],
                  "entities": []
                }
                """;
        when(client.chatCompletion(anyString())).thenReturn(invalidCategoryJson);

        assertThatThrownBy(() -> provider.enrich(new AiInput("Title", "Content", Language.EN)))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> {
                    AiProviderException ape = (AiProviderException) ex;
                    assertThat(ape.kind()).isEqualTo(AiProviderException.Kind.INVALID_RESPONSE);
                    assertThat(ape.provider()).isEqualTo("OPENROUTER");
                });
    }

    @Test
    void rejectsMissingSummary() {
        String missingSummaryJson = """
                {
                  "summary": "",
                  "category": "POLITICS",
                  "topics": [],
                  "keywords": [],
                  "entities": []
                }
                """;
        when(client.chatCompletion(anyString())).thenReturn(missingSummaryJson);

        assertThatThrownBy(() -> provider.enrich(new AiInput("Title", "Content", Language.EN)))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> {
                    AiProviderException ape = (AiProviderException) ex;
                    assertThat(ape.kind()).isEqualTo(AiProviderException.Kind.INVALID_RESPONSE);
                });
    }

    @Test
    void propagatesProviderException() {
        when(client.chatCompletion(anyString())).thenThrow(new AiProviderException(
                "OPENROUTER",
                AiProviderException.Kind.RATE_LIMIT,
                "Limited",
                429,
                "RESOURCE_EXHAUSTED",
                "Rate limit",
                "openrouter/free",
                Duration.ofSeconds(60),
                null));

        assertThatThrownBy(() -> provider.enrich(new AiInput("Title", "Content", Language.EN)))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> {
                    AiProviderException ape = (AiProviderException) ex;
                    assertThat(ape.kind()).isEqualTo(AiProviderException.Kind.RATE_LIMIT);
                    assertThat(ape.retryAfter()).isEqualTo(Duration.ofSeconds(60));
                });
    }
}
