package lk.srilankannews.translation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleSummaryResolver;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;

class TranslationInputFactoryTest {
    private final TranslationInputFactory factory =
            new TranslationInputFactory(new ArticleSummaryResolver());

    @Test
    void hashIsDeterministicUnicodeNormalizedAndVersioned() {
        Article composed = article("ශ්‍රී ලංකාව", "Café summary");
        Article decomposed = article("ශ්‍රී ලංකාව", "Cafe\u0301   summary");
        var v1 = new TranslationProperties("gemini", "translation-v1", 8000, 10);
        var v2 = new TranslationProperties("gemini", "translation-v2", 8000, 10);

        assertThat(factory.prepare(composed, v1).inputHash())
                .isEqualTo(factory.prepare(decomposed, v1).inputHash())
                .isNotEqualTo(factory.prepare(composed, v2).inputHash());
    }

    @Test
    void titleAndCurrentAiSummaryAffectHashButExtractedContentIsNotPrepared() {
        var properties = new TranslationProperties("gemini", "translation-v1", 8000, 10);
        var prepared = factory.prepare(article("தமிழ் தலைப்பு", "தமிழ் சுருக்கம்"), properties);
        assertThat(prepared.title()).isEqualTo("தமிழ் தலைப்பு");
        assertThat(prepared.summary()).isEqualTo("தமிழ் சுருக்கம்");
        assertThat(prepared.toString()).doesNotContain("PRIVATE EXTRACTED BODY");
    }

    private Article article(String title, String summary) {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        var enrichment = new ArticleAiEnrichment(
                summary, List.of(), List.of(), List.of(), "ai", "v1", now);
        return new Article("id", "source", title, "https://example.com/1",
                "https://example.com/1", Language.EN, List.of(), now, now,
                ArticleCategory.LOCAL, "PRIVATE EXTRACTED BODY", "hash", enrichment,
                ProcessingStatus.COMPLETED, now, now);
    }
}
