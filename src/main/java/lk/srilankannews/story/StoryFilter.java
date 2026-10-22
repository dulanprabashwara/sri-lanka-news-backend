/*
 * File overview: Filters or intercepts Story data and requests.
 * Area: story clustering, timelines, comparison, and Q&A.
 */
package lk.srilankannews.story;

import java.time.Instant;
import lk.srilankannews.article.ArticleCategory;

public record StoryFilter(
        ArticleCategory category,
        Instant publishedFrom,
        Instant publishedTo
) {
}
