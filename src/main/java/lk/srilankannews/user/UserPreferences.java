package lk.srilankannews.user;

import java.time.Instant;
import java.util.Set;
import lk.srilankannews.article.ArticleCategory;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user_preferences")
public record UserPreferences(
        @Id String id,
        @Indexed(name = "uk_user_preferences_user", unique = true) String userId,
        DisplayLanguagePreference preferredDisplayLanguage,
        Set<ArticleCategory> preferredCategories,
        Instant createdAt,
        Instant updatedAt
) {
    public UserPreferences {
        preferredCategories = preferredCategories == null ? Set.of() : Set.copyOf(preferredCategories);
    }
}
