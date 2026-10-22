/*
 * File overview: Defines data returned by Article Lead Media API operations.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article.api;

import lk.srilankannews.article.MediaType;

public record ArticleLeadMediaResponse(
        String url,
        MediaType type,
        String altText,
        String caption,
        String credit,
        Integer width,
        Integer height
) {
}
