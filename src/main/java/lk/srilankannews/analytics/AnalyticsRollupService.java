package lk.srilankannews.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class AnalyticsRollupService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsRollupService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final AnalyticsEventRepository eventRepository;
    private final MongoOperations mongoOperations;
    private final Clock clock;
    private final boolean enabled;

    public AnalyticsRollupService(
            AnalyticsEventRepository eventRepository,
            MongoOperations mongoOperations,
            Clock clock,
            @Value("${analytics.enabled:true}") boolean enabled) {
        this.eventRepository = eventRepository;
        this.mongoOperations = mongoOperations;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelay = 3600000) // run hourly
    public void runRollup() {
        if (!enabled) return;
        
        log.info("Starting analytics rollup");
        try {
            Instant now = clock.instant();
            Instant startOfToday = now.truncatedTo(ChronoUnit.DAYS);
            Instant startOfYesterday = startOfToday.minus(1, ChronoUnit.DAYS);
            
            // Recompute yesterday and today to be safe and idempotent
            rollupForDay(startOfYesterday, startOfToday);
            rollupForDay(startOfToday, startOfToday.plus(1, ChronoUnit.DAYS));
            
            log.info("Completed analytics rollup");
        } catch (Exception e) {
            log.error("Failed to complete analytics rollup", e);
        }
    }

    private void rollupForDay(Instant start, Instant end) {
        String dateStr = DATE_FORMATTER.format(start);
        
        try (Stream<AnalyticsEvent> events = eventRepository.findByReceivedAtBetween(start, end)) {
            // Memory bound: using simple hash map to aggregate.
            // If events become massive, this should be done via Mongo Aggregation Framework
            // For phase 35 boundaries and standard spring batching, this is fine
            Map<String, AnalyticsDailyMetric> aggregations = new HashMap<>();
            
            events.forEach(e -> {
                aggregate(aggregations, dateStr, e, e.articleId(), "ARTICLE");
                aggregate(aggregations, dateStr, e, e.storyId(), "STORY");
                aggregate(aggregations, dateStr, e, e.sourceId(), "SOURCE");
                aggregate(aggregations, dateStr, e, e.category(), "CATEGORY");
                aggregate(aggregations, dateStr, e, e.searchMode(), "SEARCH_MODE");
                if (e.eventType() == AnalyticsEventType.SEARCH_EXECUTED && e.resultCount() != null && e.resultCount() == 0) {
                    aggregate(aggregations, dateStr, e, "ZERO_RESULT", "SEARCH_OUTCOME");
                }
                // Base metric without dimensions
                aggregate(aggregations, dateStr, e, "TOTAL", "OVERALL");
            });

            for (AnalyticsDailyMetric metric : aggregations.values()) {
                // Upsert via repository by deleting existing and inserting new or custom save
                // To be idempotent and safe, find existing and update, or catch duplicate
                // This is a naive implementation; ideally use MongoTemplate upsert
                upsertMetric(metric);
            }
            
        } catch (Exception e) {
            log.error("Error processing rollup for date {}", dateStr, e);
        }
    }

    private void aggregate(Map<String, AnalyticsDailyMetric> map, String date, AnalyticsEvent e, String value, String type) {
        if (value == null || value.isBlank()) return;
        String key = date + "|" + e.eventType().name() + "|" + type + "|" + value;
        AnalyticsDailyMetric existing = map.get(key);
        if (existing == null) {
            long count = e.resultCount() != null ? e.resultCount() : 1;
            map.put(key, new AnalyticsDailyMetric(null, date, e.eventType(), type, value, count, clock.instant()));
        } else {
            long addition = e.resultCount() != null ? e.resultCount() : 1;
            map.put(key, new AnalyticsDailyMetric(null, date, e.eventType(), type, value, existing.count() + addition, clock.instant()));
        }
    }

    private void upsertMetric(AnalyticsDailyMetric metric) {
        org.springframework.data.mongodb.core.query.Query query = org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("date").is(metric.date())
                        .and("metric").is(metric.metric())
                        .and("dimensionType").is(metric.dimensionType())
                        .and("dimensionValue").is(metric.dimensionValue())
        );
        org.springframework.data.mongodb.core.query.Update update = new org.springframework.data.mongodb.core.query.Update()
                .set("count", metric.count())
                .set("updatedAt", metric.updatedAt());
        
        try {
            mongoOperations.upsert(query, update, AnalyticsDailyMetric.class);
        } catch (Exception e) {
            log.warn("Failed to upsert metric", e);
        }
    }
}
