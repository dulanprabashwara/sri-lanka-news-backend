package lk.srilankannews.article.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleTranslation;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.translation.ArticleTranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArticleLocalizationServiceTest {
    @Mock ArticleTranslationService translationService;

    @Test
    void returnsOriginalForOriginalLanguageTranslationForValidTargetAndFallbackOtherwise() {
        Article article = article();
        ArticleLocalizationService service = new ArticleLocalizationService(translationService);
        when(translationService.valid(article, Language.SI)).thenReturn(true);

        LocalizedContentResponse original = service.localize(article, Language.EN);
        LocalizedContentResponse sinhala = service.localize(article, Language.SI);
        LocalizedContentResponse tamil = service.localize(article, Language.TA);

        assertThat(original.translated()).isFalse();
        assertThat(original.fallback()).isFalse();
        assertThat(original.title()).isEqualTo("Original title");
        assertThat(sinhala.translated()).isTrue();
        assertThat(sinhala.title()).isEqualTo("සිංහල ශීර්ෂය");
        assertThat(tamil.translated()).isFalse();
        assertThat(tamil.fallback()).isTrue();
        assertThat(tamil.resolvedLanguage()).isEqualTo(Language.EN);
        assertThat(tamil.title()).isEqualTo("Original title");
    }

    private Article article() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        var enrichment = new ArticleAiEnrichment(
                "Original summary", List.of(), List.of(), List.of(), "ai", "v1", now);
        var translation = new ArticleTranslation(
                "සිංහල ශීර්ෂය", "සිංහල සාරාංශය", "gemini", "translation-v1", "hash", now);
        return new Article("id", "source", "Original title", "https://example.com/1",
                "https://example.com/1", Language.EN, List.of(), now, now,
                ArticleCategory.LOCAL, "PRIVATE BODY", "content-hash", enrichment, null,
                Map.of(Language.SI, translation), ProcessingStatus.COMPLETED, "story", now, now);
    }
}
