package lk.srilankannews.story.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.source.api.SourceApiMapper;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryFilter;
import lk.srilankannews.story.StoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class StoryApiServiceTest {

    @Mock StoryRepository storyRepository;
    @Mock ArticleRepository articleRepository;
    @Mock SourceService sourceService;

    private StoryApiService service;

    @BeforeEach
    void setUp() {
        service = new StoryApiService(
                storyRepository,
                articleRepository,
                sourceService,
                new StoryApiMapper(),
                new ArticleApiMapper(new SourceApiMapper()));
    }

    @Test
    void listsLegacyAndHybridStoriesWithFiltersAndDeterministicSorting() {
        Story lexical = story("story-1", "lexical-v1", 2);
        Story hybrid = story("story-2", "hybrid-v1", 2);
        when(storyRepository.findAll(any(StoryFilter.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(hybrid, lexical), invocation.getArgument(1), 2));
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-31T23:59:59Z");

        var response = service.list(
                0, 20, ArticleCategory.LOCAL, from, to, Sort.Direction.DESC);

        ArgumentCaptor<StoryFilter> filter = ArgumentCaptor.forClass(StoryFilter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(storyRepository).findAll(filter.capture(), pageable.capture());
        assertThat(filter.getValue()).isEqualTo(new StoryFilter(ArticleCategory.LOCAL, from, to));
        assertThat(pageable.getValue().getSort().getOrderFor("lastPublishedAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(response.content()).extracting(StorySummaryResponse::id)
                .containsExactly("story-2", "story-1");
    }

    @Test
    void loadsMembersByStoryIdAndBatchLoadsSourcesWithoutNPlusOne() {
        Story story = story("story-1", "hybrid-v1", 3);
        Source sourceOne = source("source-1", "newsfirst", Language.EN);
        Source sourceTwo = source("source-2", "hiru-news-sinhala", Language.SI);
        List<Article> articles = List.of(
                article("article-en", sourceOne.id(), Language.EN, story.id()),
                article("article-si", sourceTwo.id(), Language.SI, story.id()),
                article("article-ta", sourceOne.id(), Language.TA, story.id()));
        when(storyRepository.findById(story.id())).thenReturn(Optional.of(story));
        when(articleRepository.findByStoryId(any(), any())).thenReturn(articles);
        when(sourceService.findAllByIds(anyCollection())).thenReturn(List.of(sourceOne, sourceTwo));

        StoryDetailResponse response = service.detail(story.id());

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(articleRepository).findByStoryId(org.mockito.ArgumentMatchers.eq(story.id()), sort.capture());
        assertThat(sort.getValue().getOrderFor("publishedAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getValue().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        verify(sourceService).findAllByIds(Set.of(sourceOne.id(), sourceTwo.id()));
        verify(sourceService, never()).findById(any());
        assertThat(response.articles()).extracting(item -> item.originalLanguage())
                .containsExactly(Language.EN, Language.SI, Language.TA);
        assertThat(response.articles()).extracting(item -> item.source().slug())
                .containsExactly("newsfirst", "hiru-news-sinhala", "newsfirst");
    }

    @Test
    void resolvesArticleStoryAndDistinguishesMissingArticleOrAssignment() {
        Article assigned = article("article-1", "source-1", Language.EN, "story-1");
        Article unassigned = article("article-2", "source-1", Language.EN, null);
        Story story = story("story-1", "hybrid-v1", 2);
        when(articleRepository.findById(assigned.id())).thenReturn(Optional.of(assigned));
        when(storyRepository.findById(story.id())).thenReturn(Optional.of(story));
        when(articleRepository.findById(unassigned.id())).thenReturn(Optional.of(unassigned));
        when(articleRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(service.storyForArticle(assigned.id()).id()).isEqualTo(story.id());
        assertThatThrownBy(() -> service.storyForArticle(unassigned.id()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Story was not found.");
        assertThatThrownBy(() -> service.storyForArticle("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Article was not found.");
    }

    @Test
    void rejectsUnknownStoryDetail() {
        when(storyRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Story was not found.");
    }

    @Test
    void rejectsSingletonAndSinglePublisherStoryDetails() {
        Story singleton = story("singleton", "hybrid-v1", 1);
        Story onePublisher = story(
                "one-publisher", "hybrid-v1", 2, Set.of("source-1"));
        when(storyRepository.findById(singleton.id())).thenReturn(Optional.of(singleton));
        when(storyRepository.findById(onePublisher.id())).thenReturn(Optional.of(onePublisher));

        assertThatThrownBy(() -> service.detail(singleton.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.detail(onePublisher.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(articleRepository, never()).findByStoryId(any(), any());
    }

    private Story story(String id, String matchingVersion, long count) {
        return story(id, matchingVersion, count, Set.of("source-1", "source-2"));
    }

    private Story story(
            String id, String matchingVersion, long count, Set<String> sourceIds) {
        Instant first = Instant.parse("2026-08-30T08:00:00Z");
        return new Story(
                id, "Sri Lanka story", "representative-1", ArticleCategory.LOCAL,
                first, first.plusSeconds(7200), count, sourceIds,
                Set.of("internal-article-id"), first, first.plusSeconds(7200), matchingVersion);
    }

    private Article article(String id, String sourceId, Language language, String storyId) {
        Instant published = Instant.parse("2026-08-30T10:00:00Z");
        return new Article(
                id, sourceId, "Unicode headline " + language.code(),
                "https://example.com/" + id, "https://example.com/" + id,
                language, List.of(), published, published.plusSeconds(60),
                ArticleCategory.LOCAL, "private extracted content", "private-content-hash",
                null, lk.srilankannews.article.ProcessingStatus.COMPLETED, storyId,
                published, published.plusSeconds(60));
    }

    private Source source(String id, String slug, Language language) {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        return new Source(
                id, slug, slug, "https://example.com/" + slug, language,
                IngestionType.RSS, true, now, now);
    }
}
