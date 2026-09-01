package lk.srilankannews.story.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.config.ArticleCategoryConverter;
import lk.srilankannews.source.api.SourceSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoryController.class)
@Import(ArticleCategoryConverter.class)
class StoryControllerTest {

    private static final String STORY_ID = "507f1f77bcf86cd799439011";
    private static final String ARTICLE_ID = "507f191e810c19729de860ea";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StoryApiService storyApiService;

    @MockitoBean
    private CoverageComparisonService coverageComparisonService;

    @Test
    void listsStoriesWithDefaultPaginationAndNewestFirstSorting() throws Exception {
        when(storyApiService.list(0, 20, null, null, null, Sort.Direction.DESC))
                .thenReturn(new PagedResponse<>(List.of(summary()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/stories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(STORY_ID))
                .andExpect(jsonPath("$.content[0].sourceCount").value(2))
                .andExpect(jsonPath("$.content[0].articleIds").doesNotExist())
                .andExpect(jsonPath("$.content[0].sourceIds").doesNotExist())
                .andExpect(jsonPath("$.content[0].matchingVersion").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void passesCategoryDateFiltersPaginationAndSort() throws Exception {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-31T23:59:59Z");
        when(storyApiService.list(
                2, 10, ArticleCategory.LOCAL, from, to, Sort.Direction.ASC))
                .thenReturn(new PagedResponse<>(List.of(), 2, 10, 0, 0, false, true));

        mockMvc.perform(get("/api/v1/stories")
                        .param("page", "2")
                        .param("size", "10")
                        .param("category", "local")
                        .param("publishedFrom", from.toString())
                        .param("publishedTo", to.toString())
                        .param("sort", "lastPublishedAt,asc"))
                .andExpect(status().isOk());

        verify(storyApiService).list(
                2, 10, ArticleCategory.LOCAL, from, to, Sort.Direction.ASC);
    }

    @Test
    void returnsSafeStoryDetailForMixedLanguages() throws Exception {
        when(storyApiService.detail(STORY_ID)).thenReturn(detail());

        mockMvc.perform(get("/api/v1/stories/{storyId}", STORY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles[0].originalLanguage").value("en"))
                .andExpect(jsonPath("$.articles[1].originalLanguage").value("si"))
                .andExpect(jsonPath("$.articles[2].originalLanguage").value("ta"))
                .andExpect(jsonPath("$.articles[0].summary").value("Public summary"))
                .andExpect(jsonPath("$.articles[0].extractedContent").doesNotExist())
                .andExpect(jsonPath("$.articles[0].contentHash").doesNotExist())
                .andExpect(jsonPath("$.articles[0].semanticEmbedding").doesNotExist())
                .andExpect(jsonPath("$.matchingVersion").doesNotExist())
                .andExpect(jsonPath("$.articleIds").doesNotExist())
                .andExpect(jsonPath("$.sourceIds").doesNotExist());
    }

    @Test
    void returnsStorySummaryForArticle() throws Exception {
        when(storyApiService.storyForArticle(ARTICLE_ID)).thenReturn(summary());

        mockMvc.perform(get("/api/v1/articles/{articleId}/story", ARTICLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(STORY_ID));
    }

    @Test
    void returnsSafeCoverageComparison() throws Exception {
        CoverageComparisonResponse response = new CoverageComparisonResponse(
                STORY_ID, "Sri Lanka public story", 2, 2, true,
                List.of("Public transport"),
                List.of(new CoverageEntityResponse("Colombo", "LOCATION")),
                List.of(new SourceCoverageResponse(
                        new CoverageSourceResponse("Daily Mirror", "daily-mirror"),
                        1, List.of(Language.EN),
                        Instant.parse("2026-08-30T08:00:00Z"),
                        Instant.parse("2026-08-30T08:00:00Z"),
                        List.of(new CoverageArticleResponse(
                                ARTICLE_ID, "Public headline", "Public summary", Language.EN,
                                Instant.parse("2026-08-30T08:00:00Z"),
                                "https://example.com/article")),
                        List.of("Public transport"), List.of(),
                        List.of(new CoverageEntityResponse("Colombo", "LOCATION")), List.of())));
        when(coverageComparisonService.compare(STORY_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/stories/{storyId}/coverage", STORY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storyId").value(STORY_ID))
                .andExpect(jsonPath("$.comparisonAvailable").value(true))
                .andExpect(jsonPath("$.sources[0].articles[0].summary").value("Public summary"))
                .andExpect(jsonPath("$.sources[0].articles[0].extractedContent").doesNotExist())
                .andExpect(jsonPath("$.sources[0].articles[0].contentHash").doesNotExist())
                .andExpect(jsonPath("$.sources[0].articles[0].semanticEmbedding").doesNotExist())
                .andExpect(jsonPath("$.sources[0].articles[0].processingStatus").doesNotExist())
                .andExpect(jsonPath("$.sources[0].articles[0].model").doesNotExist())
                .andExpect(jsonPath("$.sources[0].articles[0].promptVersion").doesNotExist())
                .andExpect(jsonPath("$.matchingVersion").doesNotExist())
                .andExpect(jsonPath("$.articleIds").doesNotExist())
                .andExpect(jsonPath("$.sourceIds").doesNotExist());
    }

    @Test
    void returnsStandardNotFoundForUnknownCoverageStory() throws Exception {
        when(coverageComparisonService.compare(STORY_ID))
                .thenThrow(new ResourceNotFoundException("Story"));

        mockMvc.perform(get("/api/v1/stories/{storyId}/coverage", STORY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Story was not found."));
    }

    @Test
    void usesStandardNotFoundResponses() throws Exception {
        when(storyApiService.detail(STORY_ID)).thenThrow(new ResourceNotFoundException("Story"));
        when(storyApiService.storyForArticle(ARTICLE_ID))
                .thenThrow(new ResourceNotFoundException("Article"));

        mockMvc.perform(get("/api/v1/stories/{storyId}", STORY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Story was not found."));
        mockMvc.perform(get("/api/v1/articles/{articleId}/story", ARTICLE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Article was not found."));
    }

    @Test
    void rejectsOversizedPagesAndMalformedIds() throws Exception {
        mockMvc.perform(get("/api/v1/stories").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/stories/not-an-id"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/articles/not-an-id/story"))
                .andExpect(status().isBadRequest());
    }

    private StorySummaryResponse summary() {
        return new StorySummaryResponse(
                STORY_ID,
                "Sri Lanka public story",
                ArticleCategory.LOCAL,
                Instant.parse("2026-08-30T08:00:00Z"),
                Instant.parse("2026-08-30T10:00:00Z"),
                3,
                2);
    }

    private StoryDetailResponse detail() {
        return new StoryDetailResponse(
                summary().id(), summary().canonicalTitle(), summary().category(),
                summary().firstPublishedAt(), summary().lastPublishedAt(),
                summary().articleCount(), summary().sourceCount(),
                List.of(article(Language.EN, "01"), article(Language.SI, "02"),
                        article(Language.TA, "03")));
    }

    private ArticleResponse article(Language language, String suffix) {
        Instant publishedAt = Instant.parse("2026-08-30T10:00:00Z");
        return new ArticleResponse(
                "507f191e810c19729de86" + suffix,
                "Unicode headline " + language.code(),
                "https://example.com/article/" + suffix,
                language,
                List.of(),
                publishedAt,
                publishedAt.plusSeconds(60),
                ArticleCategory.LOCAL,
                "Public summary",
                List.of("Sri Lanka"),
                new SourceSummaryResponse("Publisher", "publisher", "https://example.com"));
    }
}
