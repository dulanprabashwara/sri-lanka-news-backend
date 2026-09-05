package lk.srilankannews.user;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;

public record UserPreferencesResponse(
        DisplayLanguagePreference preferredDisplayLanguage,
        List<ArticleCategory> preferredCategories,
        Boolean analyticsEnabled,
        Instant createdAt,
        Instant updatedAt
) {
}
