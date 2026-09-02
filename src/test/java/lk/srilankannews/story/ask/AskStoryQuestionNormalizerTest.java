package lk.srilankannews.story.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AskStoryQuestionNormalizerTest {
    private final AskStoryQuestionNormalizer normalizer = new AskStoryQuestionNormalizer(
            new AskStoryProperties("ask-story-v1", 500, 5, 6000, 24000, 3000));

    @Test
    void normalizesUnicodeAndWhitespaceWithoutChangingLanguageText() {
        assertThat(normalizer.normalize("  Cafe\u0301\t ප්‍රශ්නය  ")).isEqualTo("Café ප්‍රශ්නය");
        assertThat(normalizer.normalize("தமிழ் கேள்வி")).isEqualTo("தமிழ் கேள்வி");
    }

    @Test
    void rejectsMissingShortAndOversizedQuestionsByCodePoint() {
        assertThatThrownBy(() -> normalizer.normalize(null)).isInstanceOf(InvalidAskStoryQuestionException.class);
        assertThatThrownBy(() -> normalizer.normalize("ab")).isInstanceOf(InvalidAskStoryQuestionException.class);
        AskStoryQuestionNormalizer shortNormalizer = new AskStoryQuestionNormalizer(
                new AskStoryProperties("ask-story-v1", 5, 5, 6000, 24000, 3000));
        assertThatThrownBy(() -> shortNormalizer.normalize("abcdef"))
                .isInstanceOf(InvalidAskStoryQuestionException.class);
    }
}
