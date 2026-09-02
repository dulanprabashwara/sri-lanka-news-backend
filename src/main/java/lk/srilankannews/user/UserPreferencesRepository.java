package lk.srilankannews.user;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserPreferencesRepository extends MongoRepository<UserPreferences, String> {
    Optional<UserPreferences> findByUserId(String userId);
}
