package lk.srilankannews.story.ask;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.openrouter.OpenRouterClient;
import lk.srilankannews.ai.openrouter.OpenRouterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenRouterGroundedAnswerProvider implements GroundedAnswerProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenRouterGroundedAnswerProvider.class);
    private static final String PROVIDER = "OPENROUTER";

    private final OpenRouterClient client;
    private final OpenRouterProperties openRouterProperties;
    private final AskStoryProperties askProperties;
    private final ObjectMapper objectMapper;
    private final GroundedAnswerPromptFactory promptFactory;

    public OpenRouterGroundedAnswerProvider(
            OpenRouterClient client,
            OpenRouterProperties openRouterProperties,
            AskStoryProperties askProperties,
            ObjectMapper objectMapper,
            GroundedAnswerPromptFactory promptFactory) {
        this.client = client;
        this.openRouterProperties = openRouterProperties;
        this.askProperties = askProperties;
        this.objectMapper = objectMapper;
        this.promptFactory = promptFactory;
    }

    @Override
    public GroundedAnswerResult answer(GroundedAnswerInput input) {
        String basePrompt = promptFactory.create(input);
        String prompt = basePrompt + """

                CRITICAL INSTRUCTION FOR OUTPUT FORMAT:
                You must output ONLY a valid JSON object matching this schema:
                {
                  "answerable": true or false,
                  "answer": "Your grounded answer here, citing sources like [S1], [S2]...",
                  "citedSourceIds": ["S1", "S2"]
                }
                Do not wrap in markdown fences or commentary. Return only the JSON object.
                """;

        String rawContent = client.chatCompletion(prompt);
        String json = OpenRouterClient.extractJson(rawContent);
        if (json.isBlank()) {
            throw invalid("OpenRouter returned empty grounded answer payload", null);
        }

        try {
            Payload payload = objectMapper.readValue(json, Payload.class);
            if (payload.answer() == null || payload.answer().isBlank()
                    || payload.answer().length() > askProperties.maxAnswerCharacters()) {
                throw invalid("OpenRouter returned invalid grounded answer text", null);
            }
            List<String> citedSourceIds = payload.citedSourceIds() != null
                    ? payload.citedSourceIds()
                    : List.of();
            return new GroundedAnswerResult(payload.answerable(), payload.answer().trim(), citedSourceIds);
        } catch (JsonProcessingException exception) {
            throw invalid("OpenRouter returned malformed grounded answer JSON: " + exception.getMessage(), exception);
        }
    }

    private AiProviderException invalid(String message, Throwable cause) {
        return new AiProviderException(
                PROVIDER,
                AiProviderException.Kind.INVALID_RESPONSE,
                message,
                null,
                "MALFORMED_GROUNDED_RESPONSE",
                message,
                openRouterProperties.model(),
                cause);
    }

    private record Payload(
            @JsonProperty("answerable") boolean answerable,
            @JsonProperty("answer") String answer,
            @JsonProperty("citedSourceIds") List<String> citedSourceIds) {}
}
