/*
 * File overview: Represents a specific Duplicate Article Content failure.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article;

public class DuplicateArticleContentException extends RuntimeException {

    public DuplicateArticleContentException(String contentHash) {
        super("An article with content hash '%s' already exists.".formatted(contentHash));
    }
}
