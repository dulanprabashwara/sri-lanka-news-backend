package lk.srilankannews.notifications;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query("{ 'userId': ?0, 'readAt': null }")
    Page<Notification> findUnreadByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query(value = "{ 'userId': ?0, 'readAt': null }", count = true)
    long countUnreadByUserId(String userId);

    @Query("{ 'userId': ?0, 'readAt': null }")
    @Update("{ '$set': { 'readAt': ?1, 'expiresAt': ?2 } }")
    void markAllReadForUser(String userId, Instant now, Instant expiresAt);

    @Query("{ 'emailDelivery.status': { $in: ?0 }, 'emailDelivery.nextAttemptAt': { $lte: ?1 } }")
    List<Notification> findEmailsToDelivery(List<Notification.EmailDelivery.DeliveryStatus> statuses, Instant now);
}
