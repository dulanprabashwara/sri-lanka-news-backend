package lk.srilankannews.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lk.srilankannews.article.ArticleCategory;

public record UserPreferencesRequest(
        @NotNull DisplayLanguagePreference preferredDisplayLanguage,
        @NotNull @Size(max = 10) Set<@NotNull ArticleCategory> preferredCategories
) {
}
