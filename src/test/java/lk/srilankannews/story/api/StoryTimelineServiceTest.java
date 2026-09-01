package lk.srilankannews.story.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class StoryTimelineServiceTest {

    @Mock StoryRepository storyRepository;
    @Mock ArticleRepository articleRepository;
    @Mock SourceService sourceService;

    private StoryTimelineService service;

    @BeforeEach
    void setUp() {
        service = new StoryTimelineService(storyRepository, articleRepository, sourceService);
    }

    @Test
    void buildsChronologicalMultilingualTimelineWithStableTieBreaking() {
        Story story = story(4);
        Source alpha = source("source-a", "alpha-news");
        Source beta = source("source-b", "beta-news");
        List<Article> unordered = List.of(
                article("article-c", alpha.id(), Language.SI, "2026-08-30T08:49:00Z"),
                article("article-b", beta.id(), Language.TA, "2026-08-30T08:23:00Z"),
                article("article-a2", alpha.id(), Language.SI, "2026-08-30T08:00:00Z"),
                article("article-a1", beta.id(), Language.EN, "2026-08-30T08:00:00Z"));
        when(storyRepository.findById(story.id())).thenReturn(Optional.of(story));
        when(articleRepository.findByStoryId(eq(story.id()), any())).thenReturn(unordered);
        when(sourceService.findAllByIds(anyCollection())).thenReturn(List.of(alpha, beta));

        StoryTimelineResponse response = service.timeline(story.id());

        assertThat(response.eventCount()).isEqualTo(4);
        assertThat(response.sourceCount()).isEqualTo(2);
        assertThat(response.firstPublishedAt()).isEqualTo(Instant.parse("2026-08-30T08:00:00Z"));
        assertThat(response.lastPublishedAt()).isEqualTo(Instant.parse("2026-08-30T08:49:00Z"));
        assertThat(response.events()).extracting(TimelineEventResponse::articleId)
                .containsExactly("article-a1", "article-a2", "article-b", "article-c");
        assertThat(response.events()).extracting(TimelineEventResponse::minutesFromFirstReport)
                .containsExactly(0L, 0L, 23L, 49L);
        assertThat(response.events()).extracting(TimelineEventResponse::originalLanguage)
                .containsExactly(Language.EN, Language.SI, Language.TA, Language.SI);
        assertThat(response.events().get(1).title()).contains("සිංහල");
        assertThat(response.events()).extracting(event -> event.source().slug())
                .containsExactly("beta-news", "alpha-news", "beta-news", "alpha-news");

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(articleRepository).findByStoryId(eq(story.id()), sort.capture());
        assertThat(sort.getValue().getOrderFor("publishedAt").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getValue().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        verify(sourceService).findAllByIds(Set.of(alpha.id(), beta.id()));
        verify(sourceService, never()).findById(any());
    }

    @Test
    void returnsValidSingleReportTimeline() {
        Story story = story(1);
        Source source = source("source-a", "daily-mirror");
        Article article = article(
                "article-one", source.id(), Language.EN, "2026-08-30T08:00:00Z");
        when(storyRepository.findById(story.id())).thenReturn(Optional.of(story));
        when(articleRepository.findByStoryId(eq(story.id()), any())).thenReturn(List.of(article));
        when(sourceService.findAllByIds(anyCollection())).thenReturn(List.of(source));

        StoryTimelineResponse response = service.timeline(story.id());

        assertThat(response.eventCount()).isEqualTo(1);
        assertThat(response.sourceCount()).isEqualTo(1);
        assertThat(response.events().get(0).minutesFromFirstReport()).isZero();
        assertThat(response.firstPublishedAt()).isEqualTo(response.lastPublishedAt());
    }

    @Test
    void rejectsUnknownPendingOrInconsistentTimelineData() {
        when(storyRepository.findById("missing")).thenReturn(Optional.empty());
        when(storyRepository.findById("pending")).thenReturn(Optional.of(storyWithId("pending", 0)));
        Story story = story(1);
        when(storyRepository.findById(story.id())).thenReturn(Optional.of(story));
        when(articleRepository.findByStoryId(eq(story.id()), any())).thenReturn(List.of(
                articleWithPublishedAt("article-null", null)));

        assertThatThrownBy(() -> service.timeline("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.timeline("pending"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.timeline(story.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Timeline Article publication time is missing.");
    }

    private Story story(long count) {
        return storyWithId("story-1", count);
    }

    private Story storyWithId(String id, long count) {
        Instant first = Instant.parse("2026-08-30T08:00:00Z");
        return new Story(
                id, "Sri Lanka story", "article-a1", ArticleCategory.LOCAL,
                first, first.plusSeconds(3600), count, Set.of("source-a"), Set.of("private"),
                first, first.plusSeconds(3600), "hybrid-v1");
    }

    private Source source(String id, String slug) {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        return new Source(
                id, slug, slug, "https://example.com/" + slug, Language.EN,
                IngestionType.RSS, true, now, now);
    }

    private Article article(String id, String sourceId, Language language, String publishedAt) {
        Instant published = Instant.parse(publishedAt);
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                "Public summary " + id, List.of(), List.of("private-keyword"), List.of(),
                "private-model", "private-prompt", published.plusSeconds(60));
        String title = language == Language.SI ? "සිංහල පුවත " + id : "Headline " + id;
        return new Article(
                id, sourceId, title, "https://example.com/" + id,
                "https://example.com/" + id, language, List.of(), published,
                published.plusSeconds(60), ArticleCategory.LOCAL,
                "private extracted content", "private-content-hash-" + id,
                enrichment, ProcessingStatus.COMPLETED, "story-1", published,
                published.plusSeconds(60));
    }

    private Article articleWithPublishedAt(String id, Instant publishedAt) {
        return new Article(
                id, "source-a", "Headline", "https://example.com/" + id,
                "https://example.com/" + id, Language.EN, List.of(), publishedAt,
                Instant.parse("2026-08-30T08:00:00Z"), ArticleCategory.LOCAL,
                "private content", "private-hash", null, ProcessingStatus.COMPLETED,
                "story-1", Instant.parse("2026-08-30T08:00:00Z"),
                Instant.parse("2026-08-30T08:00:00Z"));
    }
}
