/*
 * File overview: Defines immutable Article Lead Media data passed between backend components.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article;

public record ArticleLeadMedia(
        String url,
        MediaType type,
        String altText,
        String caption,
        String credit,
        Integer width,
        Integer height,
        String discoveredFrom
) {
}
