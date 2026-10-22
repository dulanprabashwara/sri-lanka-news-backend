/*
 * File overview: Defines the allowed Processing Status values used by the backend.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article;

public enum ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    RETRYING
}
