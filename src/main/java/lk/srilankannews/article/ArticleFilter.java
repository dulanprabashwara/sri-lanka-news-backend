/*
 * File overview: Filters or intercepts Article data and requests.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article;

import lk.srilankannews.common.domain.Language;

public record ArticleFilter(
        String sourceId,
        ArticleCategory category,
        Language language
) {
}
