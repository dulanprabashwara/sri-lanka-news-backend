package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleSemanticEmbedding;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StorySemanticMatcherTest {
    private static final Instant NOW = Instant.parse("2026-08-31T06:00:00Z");
    private StorySemanticMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new StorySemanticMatcher(new StoryEmbeddingProperties(
                "gemini-embedding-2", 3, "story-semantic-v2", 1000,
                0.82, 0.90, 50));
    }

    @Test
    void sameLanguageUsesSemanticEvidenceToLiftBorderlineLexicalMatch() {
        double score = matcher.score(
                article("a", Language.EN, ArticleCategory.LOCAL, vector(1, 0, 0)),
                article("b", Language.EN, ArticleCategory.LOCAL, vector(1, 0, 0)),
                0.65);

        assertThat(score).isGreaterThanOrEqualTo(0.72);
    }

    @Test
    void sameLanguagePreservesStrongLexicalFallbackWhenEmbeddingIsMissing() {
        assertThat(matcher.score(
                article("a", Language.EN, ArticleCategory.LOCAL, null),
                article("b", Language.EN, ArticleCategory.LOCAL, null),
                0.80)).isEqualTo(0.80);
    }

    @Test
    void sharedBroadTopicWithoutLexicalEvidenceDoesNotForceMatch() {
        double score = matcher.score(
                article("a", Language.EN, ArticleCategory.LOCAL, vector(1, 0, 0)),
                article("b", Language.EN, ArticleCategory.LOCAL, vector(0.90, 0.44, 0)),
                0.0);

        assertThat(score).isLessThan(0.72);
    }

    @Test
    void crossLanguageEnglishSinhalaCanMatchOnStrongSemanticEvidence() {
        assertThat(matcher.score(
                article("a", Language.EN, ArticleCategory.LOCAL, vector(1, 0, 0)),
                article("b", Language.SI, ArticleCategory.LOCAL, vector(1, 0, 0)),
                0.0)).isEqualTo(1.0);
    }

    @Test
    void crossLanguageEnglishTamilCanMatchOnStrongSemanticEvidence() {
        assertThat(matcher.score(
                article("a", Language.EN, ArticleCategory.LOCAL, vector(0, 1, 0)),
                article("b", Language.TA, ArticleCategory.LOCAL, vector(0, 1, 0)),
                0.0)).isEqualTo(1.0);
    }

    @Test
    void crossLanguageRequiresHigherSemanticThresholdAndCompatibleEmbedding() {
        assertThat(matcher.score(
                article("a", Language.EN, ArticleCategory.LOCAL, vector(1, 0, 0)),
                article("b", Language.SI, ArticleCategory.LOCAL, vector(0.7, 0.714, 0)),
                0.0)).isZero();
        assertThat(matcher.score(
                article("a", Language.EN, ArticleCategory.LOCAL, vector(1, 0, 0)),
                article("b", Language.SI, ArticleCategory.LOCAL, null),
                0.0)).isZero();
    }

    @Test
    void conflictingKnownCategoriesRemainRejected() {
        assertThat(matcher.score(
                article("a", Language.EN, ArticleCategory.SPORTS, vector(1, 0, 0)),
                article("b", Language.SI, ArticleCategory.POLITICS, vector(1, 0, 0)),
                0.95)).isZero();
    }

    @Test
    void staleModelEmbeddingFallsBackToLexicalOnly() {
        ArticleSemanticEmbedding stale = new ArticleSemanticEmbedding(
                List.of(1.0, 0.0, 0.0), "old-model", 3,
                "story-semantic-v2", "hash", NOW);
        assertThat(matcher.score(
                article("a", Language.EN, ArticleCategory.LOCAL, stale),
                article("b", Language.EN, ArticleCategory.LOCAL, stale),
                0.61)).isEqualTo(0.61);
    }

    private ArticleSemanticEmbedding vector(double x, double y, double z) {
        return new ArticleSemanticEmbedding(
                List.of(x, y, z), "gemini-embedding-2", 3,
                "story-semantic-v2", "hash", NOW);
    }

    private Article article(
            String id,
            Language language,
            ArticleCategory category,
            ArticleSemanticEmbedding embedding) {
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                "Summary", List.of("topic"), List.of(), List.of(),
                "gemini-2.5-flash", "v1", NOW);
        return new Article(
                id, "source-" + id, "Title " + id,
                "https://example.com/" + id, "https://example.com/" + id,
                language, List.of(), NOW, NOW, category, "content", "hash-" + id,
                enrichment, embedding, ProcessingStatus.COMPLETED, null, NOW, NOW);
    }
}
