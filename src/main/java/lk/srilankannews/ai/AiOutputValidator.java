package lk.srilankannews.ai;

import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiOutputValidator {
    public static final int MAX_SUMMARY_LENGTH = 2000;
    public static final int MAX_TOPICS = 8;
    public static final int MAX_KEYWORDS = 15;
    public static final int MAX_ENTITIES = 20;
    private static final int MAX_TERM_LENGTH = 100;
    private static final int MAX_ENTITY_NAME_LENGTH = 200;
    private static final int MAX_ENTITY_TYPE_LENGTH = 80;

    public AiResult validate(AiResult result) {
        if (result == null || result.category() == null) {
            throw invalid("AI response is incomplete");
        }
        String summary = required(result.summary(), MAX_SUMMARY_LENGTH, "summary");
        List<String> topics = terms(result.topics(), MAX_TOPICS, "topics");
        List<String> keywords = terms(result.keywords(), MAX_KEYWORDS, "keywords");
        List<AiEntity> entities = entities(result.entities());
        return new AiResult(summary, result.category(), topics, keywords, entities);
    }

    private List<String> terms(List<String> values, int maximum, String field) {
        if (values == null || values.size() > maximum) {
            throw invalid(field + " exceeds its limit");
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            cleaned.add(required(value, MAX_TERM_LENGTH, field));
        }
        return List.copyOf(cleaned);
    }

    private List<AiEntity> entities(List<AiEntity> values) {
        if (values == null || values.size() > MAX_ENTITIES) {
            throw invalid("entities exceeds its limit");
        }
        return values.stream()
                .map(value -> {
                    if (value == null) {
                        throw invalid("entity is missing");
                    }
                    return new AiEntity(
                            required(value.name(), MAX_ENTITY_NAME_LENGTH, "entity name"),
                            required(value.type(), MAX_ENTITY_TYPE_LENGTH, "entity type"));
                })
                .distinct()
                .toList();
    }

    private String required(String value, int maximum, String field) {
        if (value == null) {
            throw invalid(field + " is missing");
        }
        String cleaned = value.strip();
        if (cleaned.isEmpty() || cleaned.length() > maximum) {
            throw invalid(field + " is invalid");
        }
        return cleaned;
    }

    private AiProviderException invalid(String message) {
        return new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE, message);
    }
}
