package lk.srilankannews.analytics;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.bson.Document;

@Service
public class AdminAnalyticsService {

    private final MongoOperations mongoOperations;
    private final Clock clock;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    public AdminAnalyticsService(MongoOperations mongoOperations, Clock clock) {
        this.mongoOperations = mongoOperations;
        this.clock = clock;
    }

    public Map<String, Long> getOverview(int days) {
        String startDate = getStartDate(days);
        
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("date").gte(startDate).and("dimensionType").is("OVERALL")),
                Aggregation.group("metric").sum("count").as("total")
        );

        AggregationResults<Document> results = mongoOperations.aggregate(agg, AnalyticsDailyMetric.class, Document.class);
        
        Map<String, Long> overview = new java.util.HashMap<>();
        for (Document r : results.getMappedResults()) {
            overview.put(r.get("_id").toString(), ((Number) r.get("total")).longValue());
        }
        
        // Also get unique visitors
        Aggregation visitorAgg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("date").gte(startDate)),
                Aggregation.group().count().as("total")
        );
        AggregationResults<Document> visitorResults = mongoOperations.aggregate(visitorAgg, "analytics_daily_visitors", Document.class);
        long visitors = visitorResults.getMappedResults().isEmpty() ? 0 : ((Number) visitorResults.getMappedResults().get(0).get("total")).longValue();
        overview.put("UNIQUE_VISITORS", visitors);

        return overview;
    }

    public List<Document> getTimeSeries(int days, List<AnalyticsEventType> metrics) {
        String startDate = getStartDate(days);
        
        List<String> metricNames = metrics.stream().map(Enum::name).toList();

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("date").gte(startDate)
                        .and("dimensionType").is("OVERALL")
                        .and("metric").in(metricNames)),
                Aggregation.group("date").sum("count").as("count"),
                Aggregation.sort(Sort.Direction.ASC, "_id"),
                Aggregation.project("count").and("_id").as("date").andExclude("_id")
        );

        return mongoOperations.aggregate(agg, "analytics_daily_metrics", Document.class).getMappedResults();
    }

    public List<Document> getTopContent(int days, String dimensionType, int limit) {
        String startDate = getStartDate(days);
        
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("date").gte(startDate)
                        .and("dimensionType").is(dimensionType)
                        .and("metric").in(AnalyticsEventType.ARTICLE_VIEW.name(), AnalyticsEventType.STORY_VIEW.name(), AnalyticsEventType.SOURCE_VIEW.name(), AnalyticsEventType.CATEGORY_VIEW.name())),
                Aggregation.group("dimensionValue").sum("count").as("views"),
                Aggregation.sort(Sort.Direction.DESC, "views"),
                Aggregation.limit(limit)
        );

        return mongoOperations.aggregate(agg, "analytics_daily_metrics", Document.class).getMappedResults();
    }

    /**
     * Returns aggregate counts per event type within the date range.
     * Used for Search, Recommendations, and Notifications dashboard sections.
     * Only returns aggregate counts — no raw events, no PII.
     */
    public Map<String, Long> getSectionMetrics(int days, List<AnalyticsEventType> types) {
        String startDate = getStartDate(days);

        List<String> typeNames = types.stream().map(Enum::name).toList();
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("date").gte(startDate)
                        .and("dimensionType").is("OVERALL")
                        .and("metric").in(typeNames)),
                Aggregation.group("metric").sum("count").as("total")
        );

        AggregationResults<Document> results = mongoOperations.aggregate(agg, "analytics_daily_metrics", Document.class);
        Map<String, Long> metrics = new java.util.HashMap<>();
        for (Document r : results.getMappedResults()) {
            metrics.put(r.get("_id").toString(), ((Number) r.get("total")).longValue());
        }
        return metrics;
    }

    public Map<String, Long> getDimensionMetrics(int days, AnalyticsEventType metric, String dimensionType) {
        String startDate = getStartDate(days);

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("date").gte(startDate)
                        .and("dimensionType").is(dimensionType)
                        .and("metric").is(metric.name())),
                Aggregation.group("dimensionValue").sum("count").as("total")
        );

        AggregationResults<Document> results = mongoOperations.aggregate(agg, "analytics_daily_metrics", Document.class);
        Map<String, Long> metrics = new java.util.HashMap<>();
        for (Document r : results.getMappedResults()) {
            metrics.put(r.get("_id").toString(), ((Number) r.get("total")).longValue());
        }
        return metrics;
    }

    private String getStartDate(int days) {
        if (days <= 0 || days > 90) days = 7;
        Instant start = clock.instant().minus(days, ChronoUnit.DAYS);
        return DATE_FORMATTER.format(start);
    }
}

