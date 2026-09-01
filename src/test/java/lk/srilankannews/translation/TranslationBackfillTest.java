package lk.srilankannews.translation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TranslationBackfillTest {
    @Mock ArticleRepository repository;
    @Mock ArticleTranslationService translationService;

    @Test
    void respectsConfiguredTranslationLimitWithoutOtherPipelineWork() {
        List<Article> articles = List.of(article("1"), article("2"), article("3"));
        when(repository.findByAiEnrichmentIsNotNull(any(Pageable.class))).thenReturn(articles);
        when(translationService.needsTranslation(any())).thenReturn(true);
        var backfill = new TranslationBackfill(
                repository, translationService,
                new TranslationProperties("model", "v1", 8000, 2));

        backfill.run(null);

        verify(translationService, times(2)).ensureTranslations(any());
    }

    @Test
    void zeroDisablesBackfillBeforeRepositoryAccess() {
        var backfill = new TranslationBackfill(
                repository, translationService,
                new TranslationProperties("model", "v1", 8000, 0));
        backfill.run(null);
        verify(repository, never()).findByAiEnrichmentIsNotNull(any());
        verify(translationService, never()).ensureTranslations(any());
    }

    private Article article(String id) {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        var enrichment = new ArticleAiEnrichment(
                "Summary", List.of(), List.of(), List.of(), "ai", "v1", now);
        return new Article(id, "source", "Title " + id, "https://example.com/" + id,
                "https://example.com/" + id, Language.EN, List.of(), now, now,
                ArticleCategory.LOCAL, "Private body", "hash-" + id, enrichment,
                ProcessingStatus.COMPLETED, now, now);
    }
}
