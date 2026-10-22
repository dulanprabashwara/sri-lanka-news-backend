/*
 * File overview: Represents a specific Unknown Article Source failure.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article;

public class UnknownArticleSourceException extends RuntimeException {

    public UnknownArticleSourceException(String sourceId) {
        super("Source '" + sourceId + "' does not exist.");
    }
}
