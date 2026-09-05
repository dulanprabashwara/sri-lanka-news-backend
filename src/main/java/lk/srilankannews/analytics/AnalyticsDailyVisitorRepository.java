package lk.srilankannews.analytics;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AnalyticsDailyVisitorRepository extends MongoRepository<AnalyticsDailyVisitor, String> {
    long countByDate(String date);
}
