/*
 * File overview: Defines immutable Article Entity data passed between backend components.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article;

public record ArticleEntity(String name, String type) {
}
