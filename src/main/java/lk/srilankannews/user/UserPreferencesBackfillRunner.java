package lk.srilankannews.user;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class UserPreferencesBackfillRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(UserPreferencesBackfillRunner.class);

    private final MongoOperations mongoOperations;
    private final UserPreferencesService preferencesService;

    public UserPreferencesBackfillRunner(
            MongoOperations mongoOperations, UserPreferencesService preferencesService) {
        this.mongoOperations = mongoOperations;
        this.preferencesService = preferencesService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Set<String> userIds = new HashSet<>();
            userIds.addAll(mongoOperations.findDistinct(new Query(), "userId", "user_bookmarks", String.class));
            userIds.addAll(mongoOperations.findDistinct(new Query(), "userId", "user_follows", String.class));
            userIds.addAll(mongoOperations.findDistinct(new Query(), "_id", "user_notification_preferences", String.class));

            for (String userId : userIds) {
                if (userId != null && !userId.isBlank()) {
                    preferencesService.get(userId);
                }
            }
            log.info("Verified and backfilled user preferences for {} discovered users", userIds.size());
        } catch (Exception e) {
            log.warn("Failed to backfill user preferences: {}", e.getMessage());
        }
    }
}
