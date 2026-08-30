package lk.srilankannews.config;

public class InvalidIngestionApiKeyException extends RuntimeException {

    public InvalidIngestionApiKeyException() {
        super("A valid ingestion API key is required.");
    }
}
