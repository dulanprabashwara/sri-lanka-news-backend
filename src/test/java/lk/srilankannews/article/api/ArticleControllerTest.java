package lk.srilankannews.article.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.config.ArticleCategoryConverter;
import lk.srilankannews.config.LanguageConverter;
import lk.srilankannews.source.api.SourceSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ArticleController.class)
@Import({ArticleCategoryConverter.class, LanguageConverter.class})
class ArticleControllerTest {

    private static final String ARTICLE_ID = "507f1f77bcf86cd799439011";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleApiService articleApiService;

    @Test
    void listsArticlesWithDefaultPaginationAndNewestFirstSorting() throws Exception {
        when(articleApiService.list(0, 20, null, null, null, Sort.Direction.DESC))
                .thenReturn(pageResponse());

        mockMvc.perform(get("/api/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ARTICLE_ID))
                .andExpect(jsonPath("$.content[0].source.name").value("Daily Mirror"))
                .andExpect(jsonPath("$.content[0].originalLanguage").value("en"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sourceId").doesNotExist())
                .andExpect(jsonPath("$.content[0].canonicalUrl").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].updatedAt").doesNotExist());

        verify(articleApiService).list(0, 20, null, null, null, Sort.Direction.DESC);
    }

    @Test
    void passesPaginationFiltersAndAscendingSortToService() throws Exception {
        when(articleApiService.list(
                2, 10, "daily-mirror", ArticleCategory.POLITICS, Language.SI, Sort.Direction.ASC))
                .thenReturn(new PagedResponse<>(List.of(), 2, 10, 0, 0, false, true));

        mockMvc.perform(get("/api/v1/articles")
                        .param("page", "2")
                        .param("size", "10")
                        .param("source", "daily-mirror")
                        .param("category", "politics")
                        .param("language", "si")
                        .param("sort", "publishedAt,asc"))
                .andExpect(status().isOk());

        verify(articleApiService).list(
                2, 10, "daily-mirror", ArticleCategory.POLITICS, Language.SI, Sort.Direction.ASC);
    }

    @Test
    void returnsArticleDetailUsingPublicDto() throws Exception {
        when(articleApiService.detail(ARTICLE_ID)).thenReturn(articleResponse());

        mockMvc.perform(get("/api/v1/articles/{id}", ARTICLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ARTICLE_ID))
                .andExpect(jsonPath("$.source.slug").value("daily-mirror"))
                .andExpect(jsonPath("$.source.id").doesNotExist());
    }

    @Test
    void returnsStandardNotFoundErrorForUnknownArticle() throws Exception {
        when(articleApiService.detail(ARTICLE_ID)).thenThrow(new ResourceNotFoundException("Article"));

        mockMvc.perform(get("/api/v1/articles/{id}", ARTICLE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Article was not found."));
    }

    @Test
    void rejectsMalformedMongoId() throws Exception {
        mockMvc.perform(get("/api/v1/articles/not-an-object-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/articles").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsInvalidPageCategoryLanguageAndSortInputs() throws Exception {
        mockMvc.perform(get("/api/v1/articles").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/articles").param("category", "weather"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/articles").param("language", "de"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/articles").param("sort", "title,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private PagedResponse<ArticleResponse> pageResponse() {
        return new PagedResponse<>(List.of(articleResponse()), 0, 20, 1, 1, true, true);
    }

    private ArticleResponse articleResponse() {
        Instant publishedAt = Instant.parse("2026-08-30T00:00:00Z");
        return new ArticleResponse(
                ARTICLE_ID,
                "Sri Lanka news headline",
                "https://example.com/article",
                Language.EN,
                List.of("Reporter One"),
                publishedAt,
                publishedAt.plusSeconds(60),
                ArticleCategory.POLITICS,
                new SourceSummaryResponse(
                        "Daily Mirror",
                        "daily-mirror",
                        "https://www.dailymirror.lk"));
    }
}
