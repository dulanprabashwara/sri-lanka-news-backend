package lk.srilankannews.analytics;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "analytics_daily_visitors")
@CompoundIndex(name = "idx_daily_visitors_unique", def = "{'date': 1, 'visitorKey': 1}", unique = true)
public record AnalyticsDailyVisitor(
        @Id String id,
        @Indexed String date, // YYYY-MM-DD format
        String visitorKey,
        VisitorType visitorType
) {
}
