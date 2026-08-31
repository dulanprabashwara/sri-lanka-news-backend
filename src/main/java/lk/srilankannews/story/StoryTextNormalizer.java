package lk.srilankannews.story;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class StoryTextNormalizer {

    Set<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder tokenizable = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (Character.isLetterOrDigit(codePoint)
                    || type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK) {
                tokenizable.appendCodePoint(codePoint);
            } else {
                tokenizable.append(' ');
            }
        });
        return Arrays.stream(tokenizable.toString().trim().split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    Set<String> values(Iterable<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        values.forEach(value -> normalized.addAll(tokens(value)));
        return Set.copyOf(normalized);
    }
}
