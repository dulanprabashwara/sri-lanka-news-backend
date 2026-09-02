package lk.srilankannews.story.ask;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import java.util.List;
import java.util.Map;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.GeminiFailureMapper;
import lk.srilankannews.ai.GeminiProperties;

public class GeminiGroundedAnswerProvider implements GroundedAnswerProvider {
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("answerable", "answer", "citedSourceIds"),
            "properties", Map.of(
                    "answerable", Map.of("type", "boolean"),
                    "answer", Map.of("type", "string"),
                    "citedSourceIds", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string"))));

    private final Client client;
    private final GeminiProperties geminiProperties;
    private final AskStoryProperties askProperties;
    private final ObjectMapper objectMapper;
    private final GroundedAnswerPromptFactory promptFactory;

    public GeminiGroundedAnswerProvider(
            Client client, GeminiProperties geminiProperties, AskStoryProperties askProperties,
            ObjectMapper objectMapper, GroundedAnswerPromptFactory promptFactory) {
        this.client = client;
        this.geminiProperties = geminiProperties;
        this.askProperties = askProperties;
        this.objectMapper = objectMapper;
        this.promptFactory = promptFactory;
    }

    @Override
    public GroundedAnswerResult answer(GroundedAnswerInput input) {
        try {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseJsonSchema(RESPONSE_SCHEMA)
                    .build();
            GenerateContentResponse response = client.models.generateContent(
                    geminiProperties.model(), promptFactory.create(input), config);
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw invalid("Gemini returned an empty grounded answer", null);
            }
            GeminiPayload payload = objectMapper.readValue(text, GeminiPayload.class);
            if (payload.answer() == null || payload.answer().isBlank()
                    || payload.answer().length() > askProperties.maxAnswerCharacters()) {
                throw invalid("Gemini returned an invalid grounded answer", null);
            }
            return new GroundedAnswerResult(
                    payload.answerable(), payload.answer().trim(), payload.citedSourceIds());
        } catch (AiProviderException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw invalid("Gemini returned malformed grounded output", exception);
        } catch (RuntimeException exception) {
            throw GeminiFailureMapper.map(exception, geminiProperties.model());
        }
    }

    private AiProviderException invalid(String message, Throwable cause) {
        return new AiProviderException(
                AiProviderException.Kind.INVALID_RESPONSE, message, null,
                "MALFORMED_GROUNDED_RESPONSE", message, geminiProperties.model(), cause);
    }

    private record GeminiPayload(
            boolean answerable, String answer, List<String> citedSourceIds) {
    }
}
