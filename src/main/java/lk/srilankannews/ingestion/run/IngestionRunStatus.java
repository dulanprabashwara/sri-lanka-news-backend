/*
 * File overview: Defines the allowed Ingestion Run Status values used by the backend.
 * Area: ingestion coordination and run tracking.
 */
package lk.srilankannews.ingestion.run;

public enum IngestionRunStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    INTERRUPTED
}
