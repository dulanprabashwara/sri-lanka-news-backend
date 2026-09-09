package lk.srilankannews.translation;

public class TranslationProviderException extends RuntimeException {
    public enum Kind {
        VALIDATION,
        AUTHENTICATION,
        PERMISSION,
        RATE_LIMIT,
        TIMEOUT_NETWORK,
        PROVIDER_5XX,
        PROVIDER_FAILURE,
        INVALID_RESPONSE
    }

    private final Kind kind;

    public TranslationProviderException(String message) {
        this(Kind.PROVIDER_FAILURE, message, null);
    }

    public TranslationProviderException(String message, Throwable cause) {
        this(Kind.PROVIDER_FAILURE, message, cause);
    }

    public TranslationProviderException(Kind kind, String message) {
        this(kind, message, null);
    }

    public TranslationProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean fallbackEligible() {
        return kind == Kind.RATE_LIMIT
                || kind == Kind.TIMEOUT_NETWORK
                || kind == Kind.PROVIDER_5XX
                || kind == Kind.PROVIDER_FAILURE;
    }
}
