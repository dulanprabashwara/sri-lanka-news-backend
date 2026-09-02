package lk.srilankannews.user;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;

public record UserPreferencesResponse(
        DisplayLanguagePreference preferredDisplayLanguage,
        List<ArticleCategory> preferredCategories,
        Instant createdAt,
        Instant updatedAt
) {
}
