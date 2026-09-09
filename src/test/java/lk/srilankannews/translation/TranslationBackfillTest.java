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
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.springframework.boot.DefaultApplicationArguments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class TranslationBackfillTest {
    @Mock ArticleRepository repository;
    @Mock ArticleTranslationService translationService;
    @Mock SourceService sourceService;

    @Test
    void respectsConfiguredTranslationLimitWithoutOtherPipelineWork() {
        List<Article> articles = List.of(article("1"), article("2"), article("3"));
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(articles));
        when(translationService.needsTranslation(any(), any())).thenReturn(true);
        var backfill = new TranslationBackfill(
                repository, translationService,
                new TranslationProperties("model", "v1", 8000, 2));

        backfill.run(null);

        verify(translationService, times(4)).ensureTranslations(any(), any());
    }

    @Test
    void zeroDisablesBackfillBeforeRepositoryAccess() {
        var backfill = new TranslationBackfill(
                repository, translationService,
                new TranslationProperties("model", "v1", 8000, 0));
        backfill.run(null);
        verify(repository, never()).findAll(any(Pageable.class));
        verify(translationService, never()).ensureTranslations(any());
    }

    @Test
    void explicitBackfillIsDryRunByDefaultAndDoesNotCallProvidersOrPersist() {
        Article article = article("1");
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(article)));
        when(translationService.needsTranslation(article, Language.SI)).thenReturn(true);
        var backfill = new TranslationBackfill(
                repository, translationService,
                new TranslationProperties("model", "v1", 8000, 0), sourceService);

        backfill.run(new DefaultApplicationArguments(
                "--translation-backfill", "--target-language=SI", "--max-articles=1"));

        verify(translationService).needsTranslation(article, Language.SI);
        verify(translationService, never()).ensureTranslations(any(), any());
    }

    @Test
    void applyHonorsSourceDateAndTargetFilters() {
        Article included = article("included", "source-1", "2026-09-05T00:00:00Z");
        Article wrongSource = article("wrong-source", "source-2", "2026-09-05T00:00:00Z");
        Article tooOld = article("too-old", "source-1", "2026-08-31T23:59:59Z");
        Article tooNew = article("too-new", "source-1", "2026-09-08T00:00:00Z");
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(included, wrongSource, tooOld, tooNew)));
        when(sourceService.findBySlug("newsfirst")).thenReturn(java.util.Optional.of(source()));
        when(translationService.needsTranslation(included, Language.TA)).thenReturn(true);
        when(translationService.ensureTranslations("included", Language.TA)).thenReturn(true);
        var backfill = new TranslationBackfill(
                repository, translationService,
                new TranslationProperties("model", "v1", 8000, 0), sourceService);

        backfill.run(new DefaultApplicationArguments(
                "--translation-backfill", "--apply", "--source=newsfirst",
                "--from-date=2026-09-01", "--to-date=2026-09-07",
                "--target-language=TA", "--max-articles=10"));

        verify(translationService).ensureTranslations("included", Language.TA);
        verify(translationService, times(1)).ensureTranslations(any(), any());
    }

    private Article article(String id) {
        return article(id, "source", "2026-09-01T00:00:00Z");
    }

    private Article article(String id, String sourceId, String publishedAt) {
        Instant now = Instant.parse(publishedAt);
        var enrichment = new ArticleAiEnrichment(
                "Summary", List.of(), List.of(), List.of(), "ai", "v1", now);
        return new Article(id, sourceId, "Title " + id, "https://example.com/" + id,
                "https://example.com/" + id, Language.EN, List.of(), now, now,
                ArticleCategory.LOCAL, "Private body", "hash-" + id, enrichment,
                ProcessingStatus.COMPLETED, now, now);
    }

    private Source source() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        return new Source("source-1", "NewsFirst", "newsfirst", "https://newsfirst.lk",
                Language.EN, IngestionType.RSS, true, now, now);
    }
}
