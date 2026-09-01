package lk.srilankannews.story.api;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class CoverageTextNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    String normalize(String value) {
        if (value == null) {
            return "";
        }
        String unicode = Normalizer.normalize(value, Normalizer.Form.NFC);
        return WHITESPACE.matcher(unicode.trim())
                .replaceAll(" ")
                .toLowerCase(Locale.ROOT);
    }

    String display(String value) {
        if (value == null) {
            return "";
        }
        String unicode = Normalizer.normalize(value, Normalizer.Form.NFC);
        return WHITESPACE.matcher(unicode.trim()).replaceAll(" ");
    }
}
