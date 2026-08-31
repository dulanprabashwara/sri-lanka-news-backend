package lk.srilankannews.article.cache;

import java.util.Locale;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import org.springframework.data.domain.Sort;

public record ArticleFeedQuery(
        int page,
        int size,
        String sourceSlug,
        ArticleCategory category,
        Language language,
        Sort.Direction direction) {

    public ArticleFeedQuery {
        sourceSlug = sourceSlug == null ? null : sourceSlug.trim();
    }

    public String cacheKey(String generation) {
        return "news:feed:v" + generation
                + ":page=" + page
                + ":size=" + size
                + ":source=" + value(sourceSlug)
                + ":category=" + value(category)
                + ":language=" + value(language)
                + ":sort=publishedAt," + direction.name().toLowerCase(Locale.ROOT);
    }

    private static String value(Object value) {
        return value == null ? "all" : value.toString().toLowerCase(Locale.ROOT);
    }
}
