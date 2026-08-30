package lk.srilankannews.article;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArticleContentHasherTest {
    private final ArticleContentHasher hasher = new ArticleContentHasher();

    @Test
    void normalizesWhitespaceWithoutChangingCaseOrPunctuation() {
        assertThat(hasher.hash("  ශ්‍රී\n\tLanka   news. "))
                .isEqualTo(hasher.hash("ශ්‍රී Lanka news."));
        assertThat(hasher.hash("News.")).isNotEqualTo(hasher.hash("news"));
    }

    @Test
    void normalizesCanonicallyEquivalentUnicode() {
        assertThat(hasher.hash("Caf\u00e9")).isEqualTo(hasher.hash("Cafe\u0301"));
    }

    @Test
    void returnsStableSha256Hex() {
        assertThat(hasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .hasSize(64);
    }
}
