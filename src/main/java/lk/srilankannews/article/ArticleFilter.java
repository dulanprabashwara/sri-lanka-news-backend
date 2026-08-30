package lk.srilankannews.article;

import lk.srilankannews.common.domain.Language;

public record ArticleFilter(
        String sourceId,
        ArticleCategory category,
        Language language
) {
}
