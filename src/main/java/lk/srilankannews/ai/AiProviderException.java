package lk.srilankannews.ai;

import java.util.regex.Pattern;

public class AiProviderException extends RuntimeException {
    private static final int MAX_DIAGNOSTIC_LENGTH = 500;
    private static final Pattern URL_CREDENTIALS =
            Pattern.compile("(?i)(https?://)[^\\s/@]+(?::[^\\s/@]*)?@");
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)((?:[?&]|\\b)(?:key|api[_-]?key|x-goog-api-key)\\s*[=:]\\s*)[^\\s&,]+");
    private static final Pattern BEARER_SECRET =
            Pattern.compile("(?i)(bearer\\s+)[a-z0-9._~+/-]+");
    private static final Pattern GOOGLE_API_KEY =
            Pattern.compile("AIza[0-9A-Za-z_-]{20,}");

    public enum Kind {
        AUTHENTICATION,
        PERMISSION,
        RATE_LIMIT,
        MODEL_NOT_FOUND,
        INVALID_REQUEST,
        TIMEOUT_NETWORK,
        PROVIDER_5XX,
        PROVIDER_FAILURE,
        INVALID_RESPONSE,
        UNUSABLE_INPUT
    }

    private final Kind kind;
    private final Integer httpStatus;
    private final String providerCode;
    private final String providerMessage;
    private final String model;

    public AiProviderException(Kind kind, String message) {
        this(kind, message, null, null, null, null, null);
    }

    public AiProviderException(Kind kind, String message, Throwable cause) {
        this(kind, message, null, null, null, null, cause);
    }

    public AiProviderException(
            Kind kind,
            String message,
            Integer httpStatus,
            String providerCode,
            String providerMessage,
            String model,
            Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.providerCode = sanitize(providerCode);
        this.providerMessage = sanitize(providerMessage);
        this.model = sanitize(model);
    }

    public Kind kind() {
        return kind;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public String providerCode() {
        return providerCode;
    }

    public String providerMessage() {
        return providerMessage;
    }

    public String model() {
        return model;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = URL_CREDENTIALS.matcher(value).replaceAll("$1***@");
        sanitized = QUERY_SECRET.matcher(sanitized).replaceAll("$1***");
        sanitized = BEARER_SECRET.matcher(sanitized).replaceAll("$1***");
        sanitized = GOOGLE_API_KEY.matcher(sanitized).replaceAll("***");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized.length() > MAX_DIAGNOSTIC_LENGTH
                ? sanitized.substring(0, MAX_DIAGNOSTIC_LENGTH) + "..."
                : sanitized;
    }
}
