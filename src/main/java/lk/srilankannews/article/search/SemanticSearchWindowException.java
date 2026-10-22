/*
 * File overview: Represents a specific Semantic Search Window failure.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article.search;

public class SemanticSearchWindowException extends RuntimeException {
    public SemanticSearchWindowException(int maximum) {
        super("Semantic search results are limited to the first " + maximum + " items.");
    }
}
