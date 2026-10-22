/*
 * File overview: Filters or intercepts Article Search data and requests.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article.search;

import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;

public record ArticleSearchFilter(
        String sourceId,
        ArticleCategory category,
        Language language) {
}
