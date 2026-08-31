package lk.srilankannews.processing;

import java.util.Locale;
import java.util.regex.Pattern;

final class RedisFailureDescription {
    private static final int MAX_DETAIL_LENGTH = 500;
    private static final Pattern REDIS_CREDENTIALS =
            Pattern.compile("(?i)(rediss?://)[^\\s@]+@");
    private static final Pattern PASSWORD_VALUE =
            Pattern.compile("(?i)(password\\s*[=:]\\s*)[^\\s,;]+");

    private RedisFailureDescription() {
    }

    static Details from(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return new Details(root.getClass().getSimpleName(), sanitize(root.getMessage()));
    }

    static boolean containsCode(Throwable throwable, String code) {
        String expected = code.toUpperCase(Locale.ROOT);
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toUpperCase(Locale.ROOT).contains(expected)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "(no detail)";
        }
        String sanitized = REDIS_CREDENTIALS.matcher(message).replaceAll("$1***@");
        sanitized = PASSWORD_VALUE.matcher(sanitized).replaceAll("$1***");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized.length() > MAX_DETAIL_LENGTH
                ? sanitized.substring(0, MAX_DETAIL_LENGTH) + "..."
                : sanitized;
    }

    record Details(String rootCause, String message) {
    }
}
