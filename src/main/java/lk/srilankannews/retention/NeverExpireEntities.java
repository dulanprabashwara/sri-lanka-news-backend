package lk.srilankannews.retention;

import java.util.Collections;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.ingestion.settings.IngestionSourceSettings;
import lk.srilankannews.notifications.NotificationPreference;
import lk.srilankannews.source.Source;
import lk.srilankannews.story.Story;
import lk.srilankannews.user.UserBookmark;
import lk.srilankannews.user.UserFollow;
import lk.srilankannews.user.UserPreferences;

/**
 * Authoritative registry of core domain datasets that must NEVER receive
 * automatic TTL index or deletion policies.
 */
public final class NeverExpireEntities {

    public static final Set<Class<?>> NEVER_EXPIRE_CLASSES = Collections.unmodifiableSet(Set.of(
            Article.class,
            Story.class,
            Source.class,
            UserPreferences.class,
            NotificationPreference.class,
            UserBookmark.class,
            UserFollow.class,
            IngestionSourceSettings.class
    ));

    public static final Set<String> NEVER_EXPIRE_COLLECTIONS = Collections.unmodifiableSet(Set.of(
            "articles",
            "stories",
            "sources",
            "user_preferences",
            "notification_preferences",
            "user_bookmarks",
            "user_follows",
            "ingestion_source_settings"
    ));

    private NeverExpireEntities() {
        // Un-instantiable utility class
    }

    public static boolean isNeverExpireClass(Class<?> entityClass) {
        return entityClass != null && NEVER_EXPIRE_CLASSES.contains(entityClass);
    }

    public static boolean isNeverExpireCollection(String collectionName) {
        return collectionName != null && NEVER_EXPIRE_COLLECTIONS.contains(collectionName.toLowerCase());
    }
}
