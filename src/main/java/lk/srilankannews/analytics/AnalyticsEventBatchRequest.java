package lk.srilankannews.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AnalyticsEventBatchRequest(
        String sessionId,
        @NotNull @Size(min = 1, max = 20) List<@Valid AnalyticsEventDto> events,
        String routeType
) {
}
