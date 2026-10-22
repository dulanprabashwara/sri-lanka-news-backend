/*
 * File overview: Defines MongoDB persistence and query operations for User Preferences.
 * Area: reader preferences and personalization.
 */
package lk.srilankannews.user;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserPreferencesRepository extends MongoRepository<UserPreferences, String> {
    Optional<UserPreferences> findByUserId(String userId);
}
