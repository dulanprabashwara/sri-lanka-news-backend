/*
 * File overview: Defines MongoDB persistence and query operations for Analytics Event.
 * Area: privacy-conscious analytics.
 */
package lk.srilankannews.analytics;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.Instant;
import java.util.stream.Stream;

public interface AnalyticsEventRepository extends MongoRepository<AnalyticsEvent, String> {
    Stream<AnalyticsEvent> findByReceivedAtBetween(Instant start, Instant end);
}
