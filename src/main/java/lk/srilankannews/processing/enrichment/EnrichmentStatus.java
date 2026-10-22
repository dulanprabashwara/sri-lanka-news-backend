/*
 * File overview: Defines the allowed Enrichment Status values used by the backend.
 * Area: asynchronous article processing.
 */
package lk.srilankannews.processing.enrichment;

public enum EnrichmentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    DEFERRED,
    FAILED
}
