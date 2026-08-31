package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.ai.EmbeddingProvider;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleEntity;
import lk.srilankannews.article.ArticleSemanticEmbedding;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ArticleEmbeddingServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T06:00:00Z");

    @Mock
    private ArticleService articleService;
    @Mock
    private EmbeddingProvider provider;

    private StoryEmbeddingProperties properties;
    private SemanticEmbeddingInputFactory inputFactory;
    private ArticleEmbeddingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new StoryEmbeddingProperties(
                "gemini-embedding-2", 3, "story-semantic-v2", 1000,
                0.82, 0.90, 50);
        inputFactory = new SemanticEmbeddingInputFactory(properties);
        service = new ArticleEmbeddingService(
                articleService, provider, inputFactory, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void generatesAndPersistsVersionedEmbeddingMetadata() {
        Article article = article("A flood warning was issued", "Flood warning for Colombo", null);
        when(articleService.findById("article-1")).thenReturn(Optional.of(article));
        when(provider.embed(any())).thenReturn(List.of(0.1, 0.2, 0.3));
        when(articleService.saveSemanticEmbedding(eq("article-1"), any()))
                .thenReturn(Optional.of(article));

        service.ensureEmbedding("article-1");

        ArgumentCaptor<ArticleSemanticEmbedding> captor =
                ArgumentCaptor.forClass(ArticleSemanticEmbedding.class);
        verify(articleService).saveSemanticEmbedding(eq("article-1"), captor.capture());
        ArticleSemanticEmbedding saved = captor.getValue();
        assertThat(saved.values()).containsExactly(0.1, 0.2, 0.3);
        assertThat(saved.model()).isEqualTo("gemini-embedding-2");
        assertThat(saved.dimensions()).isEqualTo(3);
        assertThat(saved.inputVersion()).isEqualTo("story-semantic-v2");
        assertThat(saved.inputHash()).hasSize(64);
        assertThat(saved.embeddedAt()).isEqualTo(NOW);
    }

    @Test
    void reusesMatchingEmbeddingWithoutProviderCall() {
        Article base = article("Title", "Summary", null);
        String hash = inputFactory.create(base).hash();
        ArticleSemanticEmbedding existing = new ArticleSemanticEmbedding(
                List.of(0.1, 0.2, 0.3), properties.model(), properties.dimensions(),
                properties.inputVersion(), hash, NOW.minusSeconds(60));
        Article article = article("Title", "Summary", existing);
        when(articleService.findById("article-1")).thenReturn(Optional.of(article));

        assertThat(service.ensureEmbedding("article-1")).isSameAs(article);

        verify(provider, never()).embed(any());
        verify(articleService, never()).saveSemanticEmbedding(any(), any());
    }

    @Test
    void changedSemanticInputRegeneratesEmbedding() {
        ArticleSemanticEmbedding stale = new ArticleSemanticEmbedding(
                List.of(0.1, 0.2, 0.3), properties.model(), properties.dimensions(),
                properties.inputVersion(), "stale-hash", NOW.minusSeconds(60));
        Article article = article("Title", "A changed summary", stale);
        when(articleService.findById("article-1")).thenReturn(Optional.of(article));
        when(provider.embed(any())).thenReturn(List.of(0.4, 0.5, 0.6));
        when(articleService.saveSemanticEmbedding(eq("article-1"), any()))
                .thenReturn(Optional.of(article));

        service.ensureEmbedding("article-1");

        verify(provider).embed(any());
        verify(articleService).saveSemanticEmbedding(eq("article-1"), any());
    }

    @Test
    void previousInputContractVersionRegeneratesEvenWithCurrentHash() {
        Article base = article("Title", "Summary", null);
        String currentHash = inputFactory.create(base).hash();
        ArticleSemanticEmbedding oldContract = new ArticleSemanticEmbedding(
                List.of(0.1, 0.2, 0.3), properties.model(), properties.dimensions(),
                "story-semantic-v1", currentHash, NOW.minusSeconds(60));
        Article article = article("Title", "Summary", oldContract);
        when(articleService.findById("article-1")).thenReturn(Optional.of(article));
        when(provider.embed(any())).thenReturn(List.of(0.4, 0.5, 0.6));
        when(articleService.saveSemanticEmbedding(eq("article-1"), any()))
                .thenReturn(Optional.of(article));

        service.ensureEmbedding("article-1");

        verify(provider).embed(any());
        ArgumentCaptor<ArticleSemanticEmbedding> saved =
                ArgumentCaptor.forClass(ArticleSemanticEmbedding.class);
        verify(articleService).saveSemanticEmbedding(eq("article-1"), saved.capture());
        assertThat(saved.getValue().inputVersion()).isEqualTo("story-semantic-v2");
        assertThat(saved.getValue().inputHash()).isEqualTo(currentHash);
    }

    @Test
    void rejectsMalformedProviderDimensionsWithoutPersistence() {
        Article article = article("Title", "Summary", null);
        when(articleService.findById("article-1")).thenReturn(Optional.of(article));
        when(provider.embed(any())).thenReturn(List.of(0.1, 0.2));

        assertThatThrownBy(() -> service.ensureEmbedding("article-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimensions");
        verify(articleService, never()).saveSemanticEmbedding(any(), any());
    }

    private Article article(
            String title, String summary, ArticleSemanticEmbedding semanticEmbedding) {
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                summary,
                List.of("weather", "colombo"),
                List.of("warning"),
                List.of(new ArticleEntity("Colombo", "LOCATION")),
                "gemini-2.5-flash",
                "v1",
                NOW.minusSeconds(30));
        return new Article(
                "article-1", "source-1", title,
                "https://example.com/article-1", "https://example.com/article-1",
                Language.EN, List.of("Reporter"), NOW.minusSeconds(3600),
                NOW.minusSeconds(3500), ArticleCategory.LOCAL,
                "Private extracted content that must not be embedded directly.",
                "content-hash", enrichment, semanticEmbedding, ProcessingStatus.COMPLETED,
                null, NOW.minusSeconds(4000), NOW.minusSeconds(30));
    }
}
