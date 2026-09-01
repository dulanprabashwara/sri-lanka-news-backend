package lk.srilankannews.story.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import lk.srilankannews.article.ArticleEntity;
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
class CoverageComparisonServiceTest {

    @Mock StoryRepository storyRepository;
    @Mock ArticleRepository articleRepository;
    @Mock SourceService sourceService;

    private CoverageComparisonService service;

    @BeforeEach
    void setUp() {
        service = new CoverageComparisonService(
                storyRepository, articleRepository, sourceService, new CoverageTextNormalizer());
    }

    @Test
    void comparesMultipleSourcesWithDeterministicNormalizedCoverage() {
        Story story = story(3, Set.of("source-a", "source-b"));
        Source sourceA = source("source-a", "alpha-news");
        Source sourceB = source("source-b", "beta-news");
        Article firstA = article(
                "article-a1", sourceA.id(), Language.EN, "2026-08-30T08:00:00Z",
                List.of(" Drug   Trafficking ", "Police", "Same source topic"),
                List.of(new ArticleEntity("Colombo", "LOCATION"),
                        new ArticleEntity("Police", "ORGANIZATION")));
        Article secondA = article(
                "article-a2", sourceA.id(), Language.SI, "2026-08-30T10:00:00Z",
                List.of("same SOURCE topic", "පොලිසිය"),
                List.of(new ArticleEntity("POLICE", "organization")));
        Article sourceBArticle = article(
                "article-b1", sourceB.id(), Language.TA, "2026-08-30T09:00:00Z",
                List.of("drug trafficking", "Court"),
                List.of(new ArticleEntity("COLOMBO", "location"),
                        new ArticleEntity("Colombo", "PERSON")));
        when(storyRepository.findById(story.id())).thenReturn(Optional.of(story));
        when(articleRepository.findByStoryId(eq(story.id()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(firstA, sourceBArticle, secondA));
        when(sourceService.findAllByIds(anyCollection())).thenReturn(List.of(sourceA, sourceB));

        CoverageComparisonResponse response = service.compare(story.id());

        assertThat(response.comparisonAvailable()).isTrue();
        assertThat(response.articleCount()).isEqualTo(3);
        assertThat(response.sourceCount()).isEqualTo(2);
        assertThat(response.sharedTopics()).containsExactly("Drug Trafficking");
        assertThat(response.sharedEntities())
                .containsExactly(new CoverageEntityResponse("Colombo", "LOCATION"));
        assertThat(response.sources()).extracting(item -> item.source().slug())
                .containsExactly("alpha-news", "beta-news");

        SourceCoverageResponse alpha = response.sources().get(0);
        assertThat(alpha.reportCount()).isEqualTo(2);
        assertThat(alpha.languages()).containsExactly(Language.EN, Language.SI);
        assertThat(alpha.firstPublishedAt()).isEqualTo(Instant.parse("2026-08-30T08:00:00Z"));
        assertThat(alpha.lastPublishedAt()).isEqualTo(Instant.parse("2026-08-30T10:00:00Z"));
        assertThat(alpha.articles()).extracting(CoverageArticleResponse::id)
                .containsExactly("article-a1", "article-a2");
        assertThat(alpha.uniqueTopics())
                .containsExactlyInAnyOrder("Police", "Same source topic", "පොලිසිය");
        assertThat(alpha.uniqueEntities())
                .containsExactly(new CoverageEntityResponse("Police", "ORGANIZATION"));

        SourceCoverageResponse beta = response.sources().get(1);
        assertThat(beta.uniqueTopics()).containsExactly("Court");
        assertThat(beta.uniqueEntities())
                .containsExactly(new CoverageEntityResponse("Colombo", "PERSON"));

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(articleRepository).findByStoryId(eq(story.id()), sort.capture());
        assertThat(sort.getValue().getOrderFor("publishedAt").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getValue().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        verify(sourceService).findAllByIds(Set.of(sourceA.id(), sourceB.id()));
        verify(sourceService, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sameTopicRepeatedWithinOneSourceIsNotShared() {
        Story story = story(2, Set.of("source-a"));
        Source source = source("source-a", "alpha-news");
        when(storyRepository.findById(story.id())).thenReturn(Optional.of(story));
        when(articleRepository.findByStoryId(eq(story.id()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        article("one", source.id(), Language.EN, "2026-08-30T08:00:00Z",
                                List.of("Economy"), List.of()),
                        article("two", source.id(), Language.EN, "2026-08-30T09:00:00Z",
                                List.of("ECONOMY"), List.of())));
        when(sourceService.findAllByIds(anyCollection())).thenReturn(List.of(source));

        CoverageComparisonResponse response = service.compare(story.id());

        assertThat(response.comparisonAvailable()).isFalse();
        assertThat(response.sourceCount()).isEqualTo(1);
        assertThat(response.sharedTopics()).isEmpty();
        assertThat(response.sources().get(0).uniqueTopics()).containsExactly("Economy");
    }

    @Test
    void rejectsUnknownOrPendingStory() {
        when(storyRepository.findById("missing")).thenReturn(Optional.empty());
        Story pending = story(0, Set.of());
        when(storyRepository.findById("pending")).thenReturn(Optional.of(new Story(
                "pending", pending.canonicalTitle(), pending.representativeArticleId(),
                pending.category(), pending.firstPublishedAt(), pending.lastPublishedAt(),
                0, Set.of(), Set.of(), pending.createdAt(), pending.updatedAt(),
                pending.matchingVersion())));

        assertThatThrownBy(() -> service.compare("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.compare("pending"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Story story(long articleCount, Set<String> sourceIds) {
        Instant first = Instant.parse("2026-08-30T08:00:00Z");
        return new Story(
                "story-1", "Sri Lanka story", "article-a1", ArticleCategory.LOCAL,
                first, first.plusSeconds(7200), articleCount, sourceIds, Set.of("private"),
                first, first.plusSeconds(7200), "hybrid-v1");
    }

    private Source source(String id, String slug) {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        return new Source(
                id, slug, slug, "https://example.com/" + slug, Language.EN,
                IngestionType.RSS, true, now, now);
    }

    private Article article(
            String id, String sourceId, Language language, String publishedAt,
            List<String> topics, List<ArticleEntity> entities) {
        Instant published = Instant.parse(publishedAt);
        ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                "Public summary " + id, topics, List.of("private-keyword"), entities,
                "private-model", "private-prompt", published.plusSeconds(60));
        return new Article(
                id, sourceId, "Headline " + id, "https://example.com/" + id,
                "https://example.com/" + id, language, List.of(), published,
                published.plusSeconds(60), ArticleCategory.LOCAL,
                "private extracted content", "private-content-hash-" + id,
                enrichment, ProcessingStatus.COMPLETED, "story-1", published,
                published.plusSeconds(60));
    }
}
