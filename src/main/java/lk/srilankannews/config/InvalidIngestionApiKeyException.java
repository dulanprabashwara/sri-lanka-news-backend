/*
 * File overview: Represents a specific Invalid Ingestion API Key failure.
 * Area: cross-cutting application configuration.
 */
package lk.srilankannews.config;

public class InvalidIngestionApiKeyException extends RuntimeException {

    public InvalidIngestionApiKeyException() {
        super("A valid ingestion API key is required.");
    }
}
