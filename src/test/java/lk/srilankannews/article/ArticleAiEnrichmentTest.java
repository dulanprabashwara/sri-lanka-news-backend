package lk.srilankannews.article;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;

class ArticleAiEnrichmentTest {

    @Test
    void persistsValidatedEnrichmentAsCompletedInternalArticleData() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        Article article = new Article(
                "article-1", "source-1", "Headline", "https://example.com/1",
                "https://example.com/1", Language.SI, List.of(), now, now,
                ArticleCategory.OTHER, "Internal content", "hash",
                ProcessingStatus.PROCESSING, now, now);
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                "සිංහල සාරාංශය", List.of("ශ්‍රී ලංකාව"), List.of("පුවත්"),
                List.of(new ArticleEntity("කොළඹ", "LOCATION")),
                "gemini-test", "v1", now);

        Article completed = article.withAiEnrichment(
                enrichment, ArticleCategory.LOCAL, now.plusSeconds(1));

        assertThat(completed.aiEnrichment()).isEqualTo(enrichment);
        assertThat(completed.aiEnrichment().summary()).isEqualTo("සිංහල සාරාංශය");
        assertThat(completed.category()).isEqualTo(ArticleCategory.LOCAL);
        assertThat(completed.processingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(completed.updatedAt()).isEqualTo(now.plusSeconds(1));
    }
}
