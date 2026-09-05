package lk.srilankannews.analytics;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "analytics_events")
public record AnalyticsEvent(
        @Id String id,
        @Indexed(unique = true) String eventId,
        AnalyticsEventType eventType,
        Instant occurredAt,
        @Indexed(expireAfter = "30d") Instant receivedAt,
        String visitorKey,
        VisitorType visitorType,
        String routeType,
        @Indexed String articleId,
        @Indexed String storyId,
        @Indexed String sourceId,
        String category,
        String language,
        String searchMode,
        Integer resultCount,
        Integer position,
        int metadataVersion
) {
}
