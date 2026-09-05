package lk.srilankannews.analytics;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "analytics_daily_metrics")
@CompoundIndex(name = "idx_daily_metrics_unique", def = "{'date': 1, 'metric': 1, 'dimensionType': 1, 'dimensionValue': 1}", unique = true)
@CompoundIndex(name = "idx_daily_metrics_date_metric", def = "{'date': 1, 'metric': 1}")
public record AnalyticsDailyMetric(
        @Id String id,
        String date, // YYYY-MM-DD format
        AnalyticsEventType metric,
        String dimensionType,
        String dimensionValue,
        long count,
        Instant updatedAt
) {
}
