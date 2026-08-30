package lk.srilankannews.processing;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record ArticleDiscoveredEvent(
        String eventId, String articleId, String sourceId, Instant occurredAt,
        int eventVersion, int attempt) {
    public static final String EVENT_TYPE = "ARTICLE_DISCOVERED";
    public static final int CURRENT_VERSION = 1;

    public ArticleDiscoveredEvent nextAttempt() {
        return new ArticleDiscoveredEvent(
                eventId, articleId, sourceId, occurredAt, eventVersion, attempt + 1);
    }

    public Map<String, String> toFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventType", EVENT_TYPE);
        fields.put("eventId", eventId);
        fields.put("articleId", articleId);
        fields.put("sourceId", sourceId);
        fields.put("occurredAt", occurredAt.toString());
        fields.put("eventVersion", Integer.toString(eventVersion));
        fields.put("attempt", Integer.toString(attempt));
        return fields;
    }

    public static ArticleDiscoveredEvent fromFields(Map<String, String> fields) {
        if (!EVENT_TYPE.equals(fields.get("eventType"))) {
            throw new IllegalArgumentException("Unsupported processing event type");
        }
        return new ArticleDiscoveredEvent(
                required(fields, "eventId"), required(fields, "articleId"),
                required(fields, "sourceId"), Instant.parse(required(fields, "occurredAt")),
                Integer.parseInt(required(fields, "eventVersion")),
                Integer.parseInt(required(fields, "attempt")));
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing event field: " + name);
        }
        return value;
    }
}
