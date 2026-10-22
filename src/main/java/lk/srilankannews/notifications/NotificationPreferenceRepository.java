/*
 * File overview: Defines MongoDB persistence and query operations for Notification Preference.
 * Area: in-app and email notifications.
 */
package lk.srilankannews.notifications;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationPreferenceRepository extends MongoRepository<NotificationPreference, String> {
}
