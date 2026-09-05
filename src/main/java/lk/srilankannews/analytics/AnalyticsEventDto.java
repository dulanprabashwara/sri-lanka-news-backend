package lk.srilankannews.analytics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnalyticsEventDto(
        @NotNull String eventId,
        @NotNull AnalyticsEventType eventType,
        String articleId,
        String storyId,
        String sourceId,
        @Size(max = 100) String category,
        @Size(max = 10) String language,
        @Size(max = 20) String searchMode,
        @Min(0) @Max(1000) Integer position,
        Long occurredAt
) {
}
