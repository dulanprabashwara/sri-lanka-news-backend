package lk.srilankannews.notifications;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface NotificationEventRepository extends MongoRepository<NotificationEvent, String> {

    @Query("{ 'status': { $in: ?0 }, 'nextAttemptAt': { $lte: ?1 } }")
    List<NotificationEvent> findEventsToRetry(List<NotificationEvent.EventStatus> statuses, Instant now);

    boolean existsByArticleIdAndEventVersion(String articleId, String eventVersion);
}
