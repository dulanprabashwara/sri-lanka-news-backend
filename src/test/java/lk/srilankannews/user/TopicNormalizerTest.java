package lk.srilankannews.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopicNormalizerTest {
    private final TopicNormalizer normalizer = new TopicNormalizer();

    @Test
    void normalizesCaseWhitespaceAndUnicodeNfcDeterministically() {
        assertThat(normalizer.normalize("  DRUG\t  Trafficking ").key())
                .isEqualTo("drug trafficking");
        assertThat(normalizer.normalize("  DRUG\t  Trafficking ").label())
                .isEqualTo("DRUG Trafficking");
        assertThat(normalizer.normalize("Cafe\u0301").key())
                .isEqualTo(normalizer.normalize("Café").key());
    }

    @Test
    void preservesSinhalaAndTamilTopicText() {
        assertThat(normalizer.normalize(" ශ්‍රී ලංකාව ").label()).isEqualTo("ශ්‍රී ලංකාව");
        assertThat(normalizer.normalize(" தமிழ் செய்திகள் ").label()).isEqualTo("தமிழ் செய்திகள்");
    }
}
