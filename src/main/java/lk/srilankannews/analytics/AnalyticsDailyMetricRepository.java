/*
 * File overview: Defines MongoDB persistence and query operations for Analytics Daily Metric.
 * Area: privacy-conscious analytics.
 */
package lk.srilankannews.analytics;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface AnalyticsDailyMetricRepository extends MongoRepository<AnalyticsDailyMetric, String> {
    List<AnalyticsDailyMetric> findByDateBetween(String start, String end);
}
