package lk.srilankannews.translation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import java.util.List;
import java.util.Map;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.GeminiFailureMapper;
import lk.srilankannews.common.domain.Language;

public class GeminiTranslationProvider implements TranslationProvider {
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("translations"),
            "properties", Map.of("translations", Map.of(
                    "type", "array",
                    "minItems", 1,
                    "maxItems", 2,
                    "items", Map.of(
                            "type", "object",
                            "additionalProperties", false,
                            "required", List.of("language", "title", "summary"),
                            "properties", Map.of(
                                    "language", Map.of("type", "string", "enum", List.of("en", "si", "ta")),
                                    "title", Map.of("type", "string"),
                                    "summary", Map.of("type", "string"))))));

    private final Client client;
    private final TranslationProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiTranslationProvider(
            Client client, TranslationProperties properties, ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TranslatedContent> translate(TranslationInput input) {
        try {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseJsonSchema(RESPONSE_SCHEMA)
                    .build();
            GenerateContentResponse response = client.models.generateContent(
                    properties.model(), prompt(input), config);
            if (response.text() == null || response.text().isBlank()) {
                throw new TranslationProviderException("Gemini returned an empty translation response.");
            }
            Payload payload = objectMapper.readValue(response.text(), Payload.class);
            return payload.translations().stream()
                    .map(item -> new TranslatedContent(
                            Language.fromCode(item.language()), item.title(), item.summary(),
                            "GEMINI", properties.model()))
                    .toList();
        } catch (TranslationProviderException exception) {
            throw exception;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new TranslationProviderException("Gemini returned malformed translation output.", exception);
        } catch (RuntimeException exception) {
            AiProviderException mapped = GeminiFailureMapper.map(exception, properties.model());
            throw new TranslationProviderException(mapKind(mapped.kind()), mapped.getMessage(), mapped);
        }
    }

    private TranslationProviderException.Kind mapKind(AiProviderException.Kind kind) {
        return switch (kind) {
            case AUTHENTICATION -> TranslationProviderException.Kind.AUTHENTICATION;
            case PERMISSION -> TranslationProviderException.Kind.PERMISSION;
            case RATE_LIMIT -> TranslationProviderException.Kind.RATE_LIMIT;
            case TIMEOUT_NETWORK -> TranslationProviderException.Kind.TIMEOUT_NETWORK;
            case PROVIDER_5XX -> TranslationProviderException.Kind.PROVIDER_5XX;
            case INVALID_REQUEST, MODEL_NOT_FOUND, UNUSABLE_INPUT ->
                    TranslationProviderException.Kind.VALIDATION;
            case INVALID_RESPONSE -> TranslationProviderException.Kind.INVALID_RESPONSE;
            case PROVIDER_FAILURE -> TranslationProviderException.Kind.PROVIDER_FAILURE;
        };
    }

    private String prompt(TranslationInput input) {
        String targets = input.targetLanguages().stream()
                .map(Language::code)
                .sorted()
                .toList()
                .toString();
        return """
                Translate the supplied news title and existing summary from %s into exactly these target languages: %s.
                Return structured JSON only. Translation must be faithful and natural journalistic language.
                Add no facts, remove no material, provide no commentary or explanation, and use no markdown.
                Preserve numbers, dates, named entities, people, organizations, places, quotations, and uncertainty accurately.
                Do not politically reinterpret the text and do not summarize beyond translating the existing summary.

                TITLE:
                %s

                EXISTING SUMMARY:
                %s
                """.formatted(input.sourceLanguage().code(), targets, input.title(), input.summary());
    }

    private record Payload(List<Item> translations) {
    }

    private record Item(String language, String title, String summary) {
    }
}
