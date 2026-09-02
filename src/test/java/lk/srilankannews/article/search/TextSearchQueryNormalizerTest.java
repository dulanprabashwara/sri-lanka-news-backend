package lk.srilankannews.article.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

class TextSearchQueryNormalizerTest {
    private final TextSearchQueryNormalizer normalizer = new TextSearchQueryNormalizer();

    @Test
    void normalizesUnicodeNfcAndWhitespaceWithoutChangingLanguageText() {
        String decomposed = Normalizer.normalize("Café", Normalizer.Form.NFD);
        assertThat(normalizer.normalize("  " + decomposed + "\t  මැතිවරණ  தமிழ்  "))
                .isEqualTo("Café මැතිවරණ தமிழ்");
    }

    @Test
    void rejectsTooShortAndTooLongNormalizedQueries() {
        assertThatThrownBy(() -> normalizer.normalize(" a "))
                .isInstanceOf(InvalidSearchQueryException.class);
        assertThatThrownBy(() -> normalizer.normalize("x".repeat(201)))
                .isInstanceOf(InvalidSearchQueryException.class);
    }
}
