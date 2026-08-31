package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleEntity;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;

class StoryMatcherTest {
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private final StoryMatcher matcher = new StoryMatcher(new StoryTextNormalizer());

    @Test
    void normalizesUnicodeCaseAndPunctuationForRelatedCoverage() {
        Article left = article(
                "a", "Sri Lanka’s Budget – 2026", Language.EN, ArticleCategory.POLITICS,
                List.of("National Budget"), List.of(new ArticleEntity("Sri Lanka", "COUNTRY")));
        Article right = article(
                "b", "SRI LANKA S BUDGET 2026", Language.EN, ArticleCategory.POLITICS,
                List.of("national budget"), List.of(new ArticleEntity("Sri Lanka", "COUNTRY")));

        assertThat(matcher.score(left, right)).isEqualTo(1.0);
    }

    @Test
    void rejectsUnrelatedArticles() {
        Article left = article(
                "a", "Parliament approves national budget", Language.EN,
                ArticleCategory.POLITICS, List.of("Budget"), List.of());
        Article right = article(
                "b", "Cricket team wins final match", Language.EN,
                ArticleCategory.SPORTS, List.of("Cricket"), List.of());

        assertThat(matcher.score(left, right)).isZero();
    }

    @Test
    void rejectsCrossLanguageLexicalMatch() {
        Article left = article(
                "a", "Colombo election result", Language.EN, ArticleCategory.POLITICS,
                List.of("Election"), List.of());
        Article right = article(
                "b", "Colombo election result", Language.SI, ArticleCategory.POLITICS,
                List.of("Election"), List.of());

        assertThat(matcher.score(left, right)).isZero();
    }

    @Test
    void rejectsConflictingCategoriesEvenWithSameTitle() {
        Article left = article(
                "a", "National update announced", Language.EN, ArticleCategory.POLITICS,
                List.of("Update"), List.of());
        Article right = article(
                "b", "National update announced", Language.EN, ArticleCategory.SPORTS,
                List.of("Update"), List.of());

        assertThat(matcher.score(left, right)).isZero();
    }

    private Article article(
            String id,
            String title,
            Language language,
            ArticleCategory category,
            List<String> topics,
            List<ArticleEntity> entities) {
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                "Summary", topics, List.of(), entities, "model", "v1", NOW);
        return new Article(
                id, "source-" + id, title, "https://example.com/" + id,
                "https://example.com/" + id, language, List.of(), NOW, NOW,
                category, "Internal content", "hash-" + id, enrichment,
                ProcessingStatus.COMPLETED, null, NOW, NOW);
    }
}
