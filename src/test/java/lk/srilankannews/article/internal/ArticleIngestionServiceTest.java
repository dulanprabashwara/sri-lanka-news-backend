package lk.srilankannews.article.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.CreateArticleCommand;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.processing.ArticleDiscoveredNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArticleIngestionServiceTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-30T05:19:00Z");
    private static final String CANONICAL_URL =
            "https://www.dailymirror.lk/breaking-news/Fixture-story/108-123456";

    @Mock
    private ArticleService articleService;

    @Mock
    private SourceService sourceService;

    @Mock
    private ArticleDiscoveredNotifier discoveredNotifier;

    private ArticleIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new ArticleIngestionService(
                articleService, sourceService, discoveredNotifier);
    }

    @Test
    void createsArticleForResolvedSourceSlug() {
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(source()));
        when(articleService.findByCanonicalUrl(CANONICAL_URL)).thenReturn(Optional.empty());
        when(articleService.create(any(CreateArticleCommand.class))).thenReturn(article());

        ArticleIngestionResponse response = ingestionService.ingest(request());

        assertThat(response.status()).isEqualTo(ArticleIngestionResponse.Status.CREATED);
        assertThat(response.articleId()).isEqualTo("article-1");
        assertThat(response.duplicateReason()).isNull();
        ArgumentCaptor<CreateArticleCommand> command = ArgumentCaptor.forClass(CreateArticleCommand.class);
        verify(articleService).create(command.capture());
        assertThat(command.getValue().sourceId()).isEqualTo("source-1");
        assertThat(command.getValue().canonicalUrl()).isEqualTo(CANONICAL_URL);
        assertThat(command.getValue().originalLanguage()).isEqualTo(Language.EN);
        assertThat(command.getValue().extractedContent()).isEqualTo("Clean fixture body");
        verify(discoveredNotifier).notifyDiscovered(article());
    }

    @Test
    void returnsDuplicateWithoutCreatingForKnownCanonicalUrl() {
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(source()));
        when(articleService.findByCanonicalUrl(CANONICAL_URL)).thenReturn(Optional.of(article()));

        ArticleIngestionResponse response = ingestionService.ingest(request());

        assertThat(response.status()).isEqualTo(ArticleIngestionResponse.Status.DUPLICATE);
        assertThat(response.articleId()).isEqualTo("article-1");
        assertThat(response.duplicateReason())
                .isEqualTo(ArticleIngestionResponse.DuplicateReason.URL_DUPLICATE);
        verify(articleService, never()).create(any());
        verify(discoveredNotifier, never()).notifyDiscovered(any());
    }

    @Test
    void returnsCreatedWhenDownstreamNotificationFailsAfterPersistence() {
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(source()));
        when(articleService.findByCanonicalUrl(CANONICAL_URL)).thenReturn(Optional.empty());
        when(articleService.create(any(CreateArticleCommand.class))).thenReturn(article());
        doThrow(new IllegalStateException("downstream failed"))
                .when(discoveredNotifier).notifyDiscovered(any());

        ArticleIngestionResponse response = ingestionService.ingest(request());

        assertThat(response.status()).isEqualTo(ArticleIngestionResponse.Status.CREATED);
        assertThat(response.articleId()).isEqualTo("article-1");
    }

    @Test
    void returnsContentDuplicateForDifferentUrlWithIdenticalContent() {
        Article existing = article();
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(source()));
        when(articleService.findByCanonicalUrl(CANONICAL_URL)).thenReturn(Optional.empty());
        when(articleService.findByExtractedContent("Clean fixture body")).thenReturn(Optional.of(existing));

        ArticleIngestionResponse response = ingestionService.ingest(request());

        assertThat(response.status()).isEqualTo(ArticleIngestionResponse.Status.DUPLICATE);
        assertThat(response.articleId()).isEqualTo("article-1");
        assertThat(response.duplicateReason())
                .isEqualTo(ArticleIngestionResponse.DuplicateReason.CONTENT_DUPLICATE);
        verify(articleService, never()).create(any());
        verify(discoveredNotifier, never()).notifyDiscovered(any());
    }

    @Test
    void rejectsUnknownSourceSlug() {
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.ingest(request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Source was not found.");
        verify(articleService, never()).create(any());
    }

    private ArticleIngestionRequest request() {
        return new ArticleIngestionRequest(
                "daily-mirror",
                "Fixture story",
                CANONICAL_URL + "?utm_source=rss",
                CANONICAL_URL,
                Language.EN,
                List.of("DM Editorial"),
                PUBLISHED_AT,
                PUBLISHED_AT.plusSeconds(60),
                ArticleCategory.LOCAL,
                "Clean fixture body");
    }

    private Source source() {
        return new Source(
                "source-1",
                "Daily Mirror",
                "daily-mirror",
                "https://www.dailymirror.lk",
                Language.EN,
                IngestionType.RSS,
                true,
                PUBLISHED_AT,
                PUBLISHED_AT);
    }

    private Article article() {
        return new Article(
                "article-1",
                "source-1",
                "Fixture story",
                CANONICAL_URL,
                CANONICAL_URL,
                Language.EN,
                List.of("DM Editorial"),
                PUBLISHED_AT,
                PUBLISHED_AT.plusSeconds(60),
                ArticleCategory.LOCAL,
                "Clean fixture body",
                PUBLISHED_AT,
                PUBLISHED_AT);
    }
}
