package lk.srilankannews.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import java.util.List;
import java.util.Map;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;

public class GeminiAiProvider implements AiProvider {
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("summary", "category", "topics", "keywords", "entities"),
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "category", Map.of("type", "string", "enum",
                            java.util.Arrays.stream(ArticleCategory.values()).map(Enum::name).toList()),
                    "topics", arraySchema(AiOutputValidator.MAX_TOPICS),
                    "keywords", arraySchema(AiOutputValidator.MAX_KEYWORDS),
                    "entities", Map.of(
                            "type", "array",
                            "maxItems", AiOutputValidator.MAX_ENTITIES,
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "required", List.of("name", "type"),
                                    "properties", Map.of(
                                            "name", Map.of("type", "string"),
                                            "type", Map.of("type", "string"))))));

    private final Client client;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiAiProvider(Client client, GeminiProperties properties, ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiResult enrich(AiInput input) {
        try {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseJsonSchema(RESPONSE_SCHEMA)
                    .build();
            GenerateContentResponse response = client.models.generateContent(
                    properties.model(), prompt(input), config);
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new AiProviderException(
                        AiProviderException.Kind.INVALID_RESPONSE,
                        "Gemini returned an empty response",
                        null,
                        "EMPTY_RESPONSE",
                        "Gemini returned an empty response",
                        properties.model(),
                        null);
            }
            GeminiPayload payload = objectMapper.readValue(text, GeminiPayload.class);
            return new AiResult(
                    payload.summary(), payload.category(), payload.topics(),
                    payload.keywords(), payload.entities(), "GEMINI", properties.model());
        } catch (AiProviderException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new AiProviderException(
                    AiProviderException.Kind.INVALID_RESPONSE,
                    "Gemini returned invalid structured output",
                    null,
                    "MALFORMED_RESPONSE",
                    "Gemini returned malformed structured output",
                    properties.model(),
                    exception);
        } catch (RuntimeException exception) {
            throw GeminiFailureMapper.map(exception, properties.model());
        }
    }

    private String prompt(AiInput input) {
        String language = input.language() == Language.SI ? "Sinhala" : "English";
        return """
                Enrich the supplied news article using only its title and content.
                Return the summary in %s, the article's original language.
                Do not translate. Do not add outside knowledge or invent facts.
                Preserve names, dates, numbers, quotations, and stated uncertainty.
                Do not infer motives. Category must be one allowed schema value.
                Topics and keywords must be concise. Entities require only name and type.
                The content may be a deterministic leading excerpt; summarize only what is supplied.

                TITLE:
                %s

                ARTICLE CONTENT:
                %s
                """.formatted(language, input.title(), input.content());
    }

    private static Map<String, Object> arraySchema(int maximum) {
        return Map.of(
                "type", "array",
                "maxItems", maximum,
                "items", Map.of("type", "string"));
    }

    private record GeminiPayload(
            String summary,
            ArticleCategory category,
            List<String> topics,
            List<String> keywords,
            List<AiEntity> entities) {
    }
}
