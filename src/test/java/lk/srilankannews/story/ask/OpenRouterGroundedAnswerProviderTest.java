package lk.srilankannews.story.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.openrouter.OpenRouterClient;
import lk.srilankannews.ai.openrouter.OpenRouterProperties;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenRouterGroundedAnswerProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenRouterProperties openRouterProperties = new OpenRouterProperties(
            "test-key", "openrouter/free", "https://openrouter.ai/api/v1", Duration.ofSeconds(10));
    private final AskStoryProperties askProperties = new AskStoryProperties(
            "ask-story-v1", 500, 5, 6000, 24000, 3000);

    private OpenRouterClient client;
    private OpenRouterGroundedAnswerProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(OpenRouterClient.class);
        provider = new OpenRouterGroundedAnswerProvider(
                client, openRouterProperties, askProperties, objectMapper,
                new GroundedAnswerPromptFactory(askProperties));
    }

    @Test
    void producesGroundedAnswerFromValidJson() {
        String json = """
                {
                  "answerable": true,
                  "answer": "The President met with IMF officials on Monday [S1]. Further talks are planned [S2].",
                  "citedSourceIds": ["S1", "S2"]
                }
                """;
        when(client.chatCompletion(anyString())).thenReturn(json);

        GroundedAnswerResult result = provider.answer(sampleInput());

        assertThat(result.answerable()).isTrue();
        assertThat(result.answer()).isEqualTo("The President met with IMF officials on Monday [S1]. Further talks are planned [S2].");
        assertThat(result.citedSourceIds()).containsExactly("S1", "S2");
    }

    @Test
    void handlesFencesAndCommentary() {
        String fenced = """
                ```json
                {
                  "answerable": false,
                  "answer": "There is no information regarding that question.",
                  "citedSourceIds": []
                }
                ```
                """;
        when(client.chatCompletion(anyString())).thenReturn(fenced);

        GroundedAnswerResult result = provider.answer(sampleInput());

        assertThat(result.answerable()).isFalse();
        assertThat(result.answer()).isEqualTo("There is no information regarding that question.");
        assertThat(result.citedSourceIds()).isEmpty();
    }

    @Test
    void throwsWhenAnswerIsBlank() {
        String blankAnswerJson = """
                {
                  "answerable": true,
                  "answer": "   ",
                  "citedSourceIds": []
                }
                """;
        when(client.chatCompletion(anyString())).thenReturn(blankAnswerJson);

        assertThatThrownBy(() -> provider.answer(sampleInput()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> {
                    AiProviderException ape = (AiProviderException) ex;
                    assertThat(ape.kind()).isEqualTo(AiProviderException.Kind.INVALID_RESPONSE);
                    assertThat(ape.provider()).isEqualTo("OPENROUTER");
                });
    }

    @Test
    void throwsWhenJsonMalformed() {
        when(client.chatCompletion(anyString())).thenReturn("Not valid JSON at all!");

        assertThatThrownBy(() -> provider.answer(sampleInput()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> {
                    AiProviderException ape = (AiProviderException) ex;
                    assertThat(ape.kind()).isEqualTo(AiProviderException.Kind.INVALID_RESPONSE);
                });
    }

    private GroundedAnswerInput sampleInput() {
        return new GroundedAnswerInput(
                "What happened?",
                Language.EN,
                "IMF Meeting",
                ArticleCategory.BUSINESS,
                Instant.parse("2026-09-10T10:00:00Z"),
                Instant.parse("2026-09-10T12:00:00Z"),
                "Story inventory text",
                List.of(new GroundedSourceContext(
                        "S1", "Daily News", "Meeting held",
                        Instant.parse("2026-09-10T10:00:00Z"), ArticleCategory.BUSINESS,
                        "Summary text", "Full bounded content")));
    }
}
