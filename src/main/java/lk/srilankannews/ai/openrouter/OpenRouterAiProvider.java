package lk.srilankannews.ai.openrouter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lk.srilankannews.ai.AiEntity;
import lk.srilankannews.ai.AiInput;
import lk.srilankannews.ai.AiProvider;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.AiResult;
import lk.srilankannews.article.ArticleCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenRouterAiProvider implements AiProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenRouterAiProvider.class);
    private static final String PROVIDER = "OPENROUTER";

    private final OpenRouterClient client;
    private final OpenRouterProperties properties;
    private final ObjectMapper objectMapper;

    public OpenRouterAiProvider(
            OpenRouterClient client,
            OpenRouterProperties properties,
            ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiResult enrich(AiInput input) {
        String prompt = buildPrompt(input);
        String rawContent = client.chatCompletion(prompt);
        String json = OpenRouterClient.extractJson(rawContent);
        if (json.isBlank()) {
            throw invalid("OpenRouter returned empty JSON enrichment payload", null);
        }

        try {
            Payload payload = objectMapper.readValue(json, Payload.class);
            if (payload.summary() == null || payload.summary().isBlank()) {
                throw invalid("Summary is missing in OpenRouter response", null);
            }
            ArticleCategory category = parseCategory(payload.category());
            List<String> topics = payload.topics() != null ? payload.topics() : List.of();
            List<String> keywords = payload.keywords() != null ? payload.keywords() : List.of();
            List<AiEntity> entities = payload.entities() != null
                    ? payload.entities().stream()
                            .filter(e -> e != null && e.name() != null && e.type() != null)
                            .map(e -> new AiEntity(e.name().trim(), e.type().trim()))
                            .toList()
                    : List.of();

            return new AiResult(
                    payload.summary().trim(),
                    category,
                    topics,
                    keywords,
                    entities,
                    PROVIDER,
                    properties.model());
        } catch (JsonProcessingException exception) {
            throw invalid("OpenRouter returned malformed JSON: " + exception.getMessage(), exception);
        }
    }

    private ArticleCategory parseCategory(String categoryStr) {
        if (categoryStr == null || categoryStr.isBlank()) {
            throw invalid("Category is missing in OpenRouter response", null);
        }
        try {
            return ArticleCategory.valueOf(categoryStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw invalid("Unknown category in OpenRouter response: " + categoryStr, e);
        }
    }

    private AiProviderException invalid(String message, Throwable cause) {
        return new AiProviderException(
                PROVIDER,
                AiProviderException.Kind.INVALID_RESPONSE,
                message,
                null,
                "MALFORMED_RESPONSE",
                message,
                properties.model(),
                cause);
    }

    private String buildPrompt(AiInput input) {
        return """
                You are an expert news analysis AI.
                Analyze the following Sri Lankan news article and extract structured metadata.
                Respond ONLY with a valid JSON object matching the schema below.
                Do not include markdown code fences, backticks, comments, or any conversational text before or after the JSON.

                SCHEMA:
                {
                  "summary": "Concise factual summary of the article in English (1-3 sentences)",
                  "category": "One of: POLITICS, BUSINESS, SPORTS, ENTERTAINMENT, TECHNOLOGY, HEALTH, SCIENCE, WORLD, LOCAL, OTHER",
                  "topics": ["topic1", "topic2"],
                  "keywords": ["keyword1", "keyword2"],
                  "entities": [
                    {"name": "Entity Name", "type": "PERSON | ORGANIZATION | LOCATION | EVENT"}
                  ]
                }

                ARTICLE TITLE: %s
                ARTICLE LANGUAGE: %s
                ARTICLE CONTENT:
                %s
                """.formatted(input.title(), input.language(), input.content());
    }

    private record Payload(
            @JsonProperty("summary") String summary,
            @JsonProperty("category") String category,
            @JsonProperty("topics") List<String> topics,
            @JsonProperty("keywords") List<String> keywords,
            @JsonProperty("entities") List<EntityPayload> entities) {}

    private record EntityPayload(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type) {}
}
