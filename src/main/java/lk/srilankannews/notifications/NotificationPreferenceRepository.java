package lk.srilankannews.notifications;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationPreferenceRepository extends MongoRepository<NotificationPreference, String> {
}
