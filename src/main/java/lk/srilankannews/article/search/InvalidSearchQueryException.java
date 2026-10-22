/*
 * File overview: Represents a specific Invalid Search Query failure.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article.search;

public class InvalidSearchQueryException extends RuntimeException {
    public InvalidSearchQueryException(String message) {
        super(message);
    }
}
