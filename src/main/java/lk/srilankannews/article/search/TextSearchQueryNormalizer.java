package lk.srilankannews.article.search;

import java.text.Normalizer;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TextSearchQueryNormalizer {
    public static final int MAX_QUERY_LENGTH = 200;
    private static final int MIN_QUERY_CODE_POINTS = 2;
    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    public String normalize(String query) {
        String normalized = WHITESPACE.matcher(
                Normalizer.normalize(query, Normalizer.Form.NFC).trim()).replaceAll(" ");
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints < MIN_QUERY_CODE_POINTS) {
            throw new InvalidSearchQueryException(
                    "Search query must contain at least 2 Unicode characters.");
        }
        if (codePoints > MAX_QUERY_LENGTH) {
            throw new InvalidSearchQueryException(
                    "Search query must not exceed 200 Unicode characters.");
        }
        return normalized;
    }
}
