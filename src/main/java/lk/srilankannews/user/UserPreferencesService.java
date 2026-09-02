package lk.srilankannews.user;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lk.srilankannews.article.ArticleCategory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class UserPreferencesService {
    private final UserPreferencesRepository repository;
    private final MongoOperations mongoOperations;
    private final Clock clock;

    public UserPreferencesService(
            UserPreferencesRepository repository, MongoOperations mongoOperations, Clock clock) {
        this.repository = repository;
        this.mongoOperations = mongoOperations;
        this.clock = clock;
    }

    public UserPreferencesResponse get(String userId) {
        return repository.findByUserId(userId).map(this::toResponse)
                .orElseGet(() -> new UserPreferencesResponse(
                        DisplayLanguagePreference.ORIGINAL, List.of(), null, null));
    }

    public UserPreferencesResponse update(String userId, UserPreferencesRequest request) {
        Instant now = clock.instant();
        Query query = Query.query(Criteria.where("userId").is(userId));
        Update update = new Update()
                .setOnInsert("userId", userId)
                .setOnInsert("createdAt", now)
                .set("preferredDisplayLanguage", request.preferredDisplayLanguage())
                .set("preferredCategories", Set.copyOf(request.preferredCategories()))
                .set("updatedAt", now);
        UserPreferences saved;
        try {
            saved = mongoOperations.findAndModify(
                    query, update, FindAndModifyOptions.options().upsert(true).returnNew(true),
                    UserPreferences.class);
        } catch (DuplicateKeyException concurrentCreate) {
            saved = mongoOperations.findAndModify(
                    query, update, FindAndModifyOptions.options().returnNew(true),
                    UserPreferences.class);
        }
        if (saved == null) throw new IllegalStateException("Preferences update did not return a result.");
        return toResponse(saved);
    }

    private UserPreferencesResponse toResponse(UserPreferences preferences) {
        List<ArticleCategory> categories = preferences.preferredCategories().stream()
                .sorted(Comparator.comparing(Enum::name)).toList();
        return new UserPreferencesResponse(preferences.preferredDisplayLanguage(), categories,
                preferences.createdAt(), preferences.updatedAt());
    }
}
