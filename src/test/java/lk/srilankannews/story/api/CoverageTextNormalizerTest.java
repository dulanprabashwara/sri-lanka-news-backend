package lk.srilankannews.story.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoverageTextNormalizerTest {

    private final CoverageTextNormalizer normalizer = new CoverageTextNormalizer();

    @Test
    void normalizesUnicodeWhitespaceAndCaseConservatively() {
        assertThat(normalizer.normalize("  DRUG\tTrafficking  "))
                .isEqualTo("drug trafficking");
        assertThat(normalizer.normalize("Cafe\u0301")).isEqualTo("café");
        assertThat(normalizer.display("  සිංහල\n පුවත ")).isEqualTo("සිංහල පුවත");
        assertThat(normalizer.normalize("Police")).isNotEqualTo(normalizer.normalize("පොලිසිය"));
        assertThat(normalizer.normalize("Crime"))
                .isNotEqualTo(normalizer.normalize("Criminal Investigation"));
    }
}
