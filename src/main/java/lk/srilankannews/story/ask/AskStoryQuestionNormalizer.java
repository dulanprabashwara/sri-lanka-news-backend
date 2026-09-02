package lk.srilankannews.story.ask;

import java.text.Normalizer;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AskStoryQuestionNormalizer {
    private static final int MIN_CODE_POINTS = 3;
    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final AskStoryProperties properties;

    public AskStoryQuestionNormalizer(AskStoryProperties properties) {
        this.properties = properties;
    }

    public String normalize(String question) {
        if (question == null) {
            throw new InvalidAskStoryQuestionException("Question is required.");
        }
        String normalized = WHITESPACE.matcher(
                Normalizer.normalize(question, Normalizer.Form.NFC).trim()).replaceAll(" ");
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_CODE_POINTS) {
            throw new InvalidAskStoryQuestionException(
                    "Question must contain at least 3 Unicode characters.");
        }
        if (length > properties.maxQuestionCharacters()) {
            throw new InvalidAskStoryQuestionException(
                    "Question must not exceed " + properties.maxQuestionCharacters()
                            + " Unicode characters.");
        }
        return normalized;
    }
}
