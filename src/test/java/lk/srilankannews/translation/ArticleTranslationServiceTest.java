package lk.srilankannews.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleTranslation;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.article.cache.ArticleFeedCache;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArticleTranslationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    @Mock ArticleService articleService;
    @Mock TranslationProvider provider;
    @Mock ArticleFeedCache feedCache;
    private TranslationProperties properties;
    private TranslationInputFactory inputFactory;
    private ArticleTranslationService service;

    @BeforeEach
    void setUp() {
        properties = new TranslationProperties("gemini-test", "translation-v1", 8000, 10);
        inputFactory = new TranslationInputFactory();
        service = new ArticleTranslationService(
                articleService, provider, inputFactory, new TranslationOutputValidator(),
                properties, feedCache, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void englishArticleUsesOneCallForSinhalaAndTamilAndPersistsUnicode() {
        Article article = article(Language.EN, "English title", "English summary", Map.of());
        when(articleService.findById(article.id())).thenReturn(Optional.of(article));
        when(provider.translate(any())).thenReturn(List.of(
                new TranslatedContent(Language.SI, "සිංහල ශීර්ෂය", "සිංහල සාරාංශය"),
                new TranslatedContent(Language.TA, "தமிழ் தலைப்பு", "தமிழ் சுருக்கம்")));
        when(articleService.saveTranslations(any(), any())).thenReturn(Optional.of(article));

        assertThat(service.ensureTranslations(article.id())).isTrue();

        ArgumentCaptor<TranslationInput> input = ArgumentCaptor.forClass(TranslationInput.class);
        verify(provider).translate(input.capture());
        assertThat(input.getValue().sourceLanguage()).isEqualTo(Language.EN);
        assertThat(input.getValue().targetLanguages()).containsExactlyInAnyOrder(Language.SI, Language.TA);
        assertThat(input.getValue().toString()).doesNotContain("PRIVATE EXTRACTED BODY");
        ArgumentCaptor<Map<Language, ArticleTranslation>> stored = ArgumentCaptor.forClass(Map.class);
        verify(articleService).saveTranslations(org.mockito.ArgumentMatchers.eq(article.id()), stored.capture());
        assertThat(stored.getValue().get(Language.SI).title()).isEqualTo("සිංහල ශීර්ෂය");
        assertThat(stored.getValue().get(Language.TA).summary()).isEqualTo("தமிழ் சுருக்கம்");
        verify(feedCache).invalidate();
    }

    @Test
    void sinhalaAndTamilNeverRequestTheirOriginalLanguage() {
        assertTargets(Language.SI, Language.EN, Language.TA);
        assertTargets(Language.TA, Language.EN, Language.SI);
    }

    @Test
    void validTranslationsAreReusedWithoutProviderOrCacheInvalidation() {
        Article base = article(Language.EN, "Title", "Summary", Map.of());
        String hash = inputFactory.prepare(base, properties).inputHash();
        Map<Language, ArticleTranslation> existing = new EnumMap<>(Language.class);
        existing.put(Language.SI, translation("සිංහල", "සාරාංශය", hash));
        existing.put(Language.TA, translation("தமிழ்", "சுருக்கம்", hash));
        Article article = article(Language.EN, "Title", "Summary", existing);
        when(articleService.findById(article.id())).thenReturn(Optional.of(article));

        assertThat(service.ensureTranslations(article.id())).isFalse();
        verify(provider, never()).translate(any());
        verify(articleService, never()).saveTranslations(any(), any());
        verify(feedCache, never()).invalidate();
    }

    @Test
    void titleOnlyArticleWithoutAiEnrichmentStillReceivesMissingTranslations() {
        Article article = new Article(
                "article-title-only", "source", "English title",
                "https://example.com/title-only", "https://example.com/title-only",
                Language.EN, List.of(), NOW, NOW, ArticleCategory.LOCAL,
                "Private body", "hash-title-only", ProcessingStatus.PENDING, NOW, NOW);
        when(articleService.findById(article.id())).thenReturn(Optional.of(article));
        when(provider.translate(any())).thenReturn(List.of(
                new TranslatedContent(Language.SI, "සිංහල ශීර්ෂය", null),
                new TranslatedContent(Language.TA, "தமிழ் தலைப்பு", null)));
        when(articleService.saveTranslations(any(), any())).thenReturn(Optional.of(article));

        assertThat(service.ensureTranslations(article.id())).isTrue();

        ArgumentCaptor<TranslationInput> input = ArgumentCaptor.forClass(TranslationInput.class);
        verify(provider).translate(input.capture());
        assertThat(input.getValue().summary()).isEmpty();
        assertThat(input.getValue().targetLanguages())
                .containsExactlyInAnyOrder(Language.SI, Language.TA);
    }

    @Test
    void changedSummaryOrTitleMakesTranslationStaleAndFailurePreservesStoredArticle() {
        Article old = article(Language.EN, "Old title", "Old summary", Map.of());
        String oldHash = inputFactory.prepare(old, properties).inputHash();
        Map<Language, ArticleTranslation> existing = Map.of(
                Language.SI, translation("පරණ", "පරණ සාරාංශය", oldHash),
                Language.TA, translation("பழைய", "பழைய சுருக்கம்", oldHash));
        Article changed = article(Language.EN, "New title", "New summary", existing);
        when(articleService.findById(changed.id())).thenReturn(Optional.of(changed));
        when(provider.translate(any())).thenThrow(new TranslationProviderException("quota"));

        assertThatThrownBy(() -> service.ensureTranslations(changed.id()))
                .isInstanceOf(TranslationProviderException.class);
        verify(articleService, never()).saveTranslations(any(), any());
        verify(feedCache, never()).invalidate();
        assertThat(changed.aiEnrichment()).isNotNull();
        assertThat(changed.storyId()).isEqualTo("story-1");
    }

    private void assertTargets(Language original, Language first, Language second) {
        org.mockito.Mockito.clearInvocations(provider, articleService, feedCache);
        Article article = article(original, "Title", "Summary", Map.of());
        when(articleService.findById(article.id())).thenReturn(Optional.of(article));
        org.mockito.Mockito.doAnswer(invocation -> {
            TranslationInput input = invocation.getArgument(0);
            return input.targetLanguages().stream()
                    .map(language -> new TranslatedContent(language, "Title " + language, "Summary " + language))
                    .toList();
        }).when(provider).translate(any());
        when(articleService.saveTranslations(any(), any())).thenReturn(Optional.of(article));
        service.ensureTranslations(article.id());
        ArgumentCaptor<TranslationInput> input = ArgumentCaptor.forClass(TranslationInput.class);
        verify(provider).translate(input.capture());
        assertThat(input.getValue().targetLanguages()).containsExactlyInAnyOrder(first, second);
    }

    private ArticleTranslation translation(String title, String summary, String hash) {
        return new ArticleTranslation(
                title, summary, properties.model(), properties.promptVersion(), hash, NOW);
    }

    private Article article(
            Language language, String title, String summary,
            Map<Language, ArticleTranslation> translations) {
        var enrichment = new ArticleAiEnrichment(
                summary, List.of("topic"), List.of(), List.of(), "ai", "v1", NOW);
        return new Article("article-" + language.code(), "source", title,
                "https://example.com/" + language.code(),
                "https://example.com/" + language.code(), language, List.of(), NOW, NOW,
                ArticleCategory.LOCAL, "PRIVATE EXTRACTED BODY", "hash-" + language.code(),
                enrichment, null, translations, ProcessingStatus.COMPLETED, "story-1", NOW, NOW);
    }
}
