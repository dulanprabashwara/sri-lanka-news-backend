package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleEntity;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;

class SemanticEmbeddingInputFactoryTest {
    private static final Instant NOW = Instant.parse("2026-08-31T06:00:00Z");
    private final SemanticEmbeddingInputFactory factory = new SemanticEmbeddingInputFactory(
            new StoryEmbeddingProperties(
                    "gemini-embedding-2", 3, "story-semantic-v2", 1000,
                    0.82, 0.90, 50));

    @Test
    void buildsDeterministicNormalizedInputWithoutExtractedContent() throws Exception {
        Article first = article(
                "Cafe\u0301   update", "  Summary\nwith spacing ",
                List.of("zeta", "alpha"),
                List.of(new ArticleEntity("Colombo", "LOCATION"),
                        new ArticleEntity("Council", "ORGANIZATION")),
                "SECRET BODY ONE");
        Article second = article(
                "Caf\u00e9 update", "Summary with spacing",
                List.of("alpha", "zeta"),
                List.of(new ArticleEntity("Council", "ORGANIZATION"),
                        new ArticleEntity("Colombo", "LOCATION")),
                "SECRET BODY TWO");

        SemanticEmbeddingInputFactory.Input left = factory.create(first);
        SemanticEmbeddingInputFactory.Input right = factory.create(second);

        assertThat(left.text()).isEqualTo(right.text());
        assertThat(left.hash()).isEqualTo(right.hash()).hasSize(64);
        assertThat(left.hash()).isEqualTo(sha256(left.text()));
        assertThat(left.text())
                .startsWith("task: sentence similarity | query: ")
                .contains("story-semantic-v2", "Caf\u00e9 update", "alpha | zeta")
                .doesNotContain("SECRET BODY");
    }

    @Test
    void buildsFallbackInputWhenAiEnrichmentIsDeferred() {
        Article article = new Article(
                "article-2", "source-1", "Fallback headline",
                "https://example.com/2", "https://example.com/2",
                Language.EN, List.of(), NOW, NOW, ArticleCategory.LOCAL,
                "The first meaningful sentence explains the event clearly. "
                        + "A second sentence supplies additional verified context.",
                "hash-2", ProcessingStatus.PENDING, NOW, NOW);

        SemanticEmbeddingInputFactory.Input input = factory.create(article);

        assertThat(input.text())
                .contains("Fallback headline")
                .contains("first meaningful sentence")
                .contains("topics: ")
                .doesNotContain("null");
    }

    private String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private Article article(
            String title,
            String summary,
            List<String> topics,
            List<ArticleEntity> entities,
            String extractedContent) {
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                summary, topics, List.of("keyword"), entities,
                "gemini-2.5-flash", "v1", NOW);
        return new Article(
                "article-1", "source-1", title,
                "https://example.com/1", "https://example.com/1",
                Language.EN, List.of(), NOW, NOW, ArticleCategory.LOCAL,
                extractedContent, "hash", enrichment, null,
                ProcessingStatus.COMPLETED, null, NOW, NOW);
    }
}
