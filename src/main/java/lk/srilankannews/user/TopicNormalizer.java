package lk.srilankannews.user;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TopicNormalizer {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    public NormalizedTopic normalize(String topic) {
        String label = WHITESPACE.matcher(Normalizer.normalize(topic, Normalizer.Form.NFC).trim())
                .replaceAll(" ");
        return new NormalizedTopic(label.toLowerCase(Locale.ROOT), label);
    }

    public record NormalizedTopic(String key, String label) {
    }
}
