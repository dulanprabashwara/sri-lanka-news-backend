package lk.srilankannews.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.SourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T10:15:30Z");

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private SourceService sourceService;

    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        articleService = new ArticleService(
                articleRepository,
                sourceService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsArticleForExistingSourceWithUtcAuditTimestamps() {
        CreateArticleCommand command = validCommand(List.of("Reporter One"));
        when(sourceService.existsById(command.sourceId())).thenReturn(true);
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Article created = articleService.create(command);

        assertThat(created.sourceId()).isEqualTo(command.sourceId());
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.updatedAt()).isEqualTo(NOW);
        assertThat(created.authors()).containsExactly("Reporter One");
        verify(articleRepository).existsByCanonicalUrl(command.canonicalUrl());
        verify(articleRepository).save(created);
    }

    @Test
    void articleAuthorsAreDefensivelyCopied() {
        List<String> authors = new ArrayList<>(List.of("Reporter One"));
        CreateArticleCommand command = validCommand(authors);
        authors.add("Reporter Two");
        when(sourceService.existsById(command.sourceId())).thenReturn(true);
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Article created = articleService.create(command);

        assertThat(created.authors()).containsExactly("Reporter One");
        assertThatThrownBy(() -> created.authors().add("Reporter Three"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsArticleWhenSourceDoesNotExist() {
        CreateArticleCommand command = validCommand(List.of());

        assertThatThrownBy(() -> articleService.create(command))
                .isInstanceOf(UnknownArticleSourceException.class);

        verify(articleRepository, never()).save(any());
    }

    @Test
    void rejectsKnownCanonicalUrlBeforeSaving() {
        CreateArticleCommand command = validCommand(List.of());
        when(sourceService.existsById(command.sourceId())).thenReturn(true);
        when(articleRepository.existsByCanonicalUrl(command.canonicalUrl())).thenReturn(true);

        assertThatThrownBy(() -> articleService.create(command))
                .isInstanceOf(DuplicateArticleCanonicalUrlException.class);

        verify(articleRepository, never()).save(any());
    }

    @Test
    void translatesDatabaseDuplicateFromConcurrentInsert() {
        CreateArticleCommand command = validCommand(List.of());
        when(sourceService.existsById(command.sourceId())).thenReturn(true);
        when(articleRepository.save(any(Article.class)))
                .thenThrow(new DuplicateKeyException("uk_articles_canonical_url"));

        assertThatThrownBy(() -> articleService.create(command))
                .isInstanceOf(DuplicateArticleCanonicalUrlException.class);
    }

    private CreateArticleCommand validCommand(List<String> authors) {
        Instant publishedAt = Instant.parse("2026-08-30T09:00:00Z");
        return new CreateArticleCommand(
                "source-1",
                "Sri Lanka news headline",
                "https://example.com/news/article?utm_source=feed",
                "https://example.com/news/article",
                Language.EN,
                authors,
                publishedAt,
                publishedAt.plusSeconds(60),
                ArticleCategory.LOCAL);
    }
}
