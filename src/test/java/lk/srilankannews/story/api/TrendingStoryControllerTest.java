package lk.srilankannews.story.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.config.ArticleCategoryConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TrendingStoryController.class)
@Import(ArticleCategoryConverter.class)
class TrendingStoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TrendingStoryService service;

    @Test
    void guestGetsDefaultTrendingStoriesWithSafePublicShape() throws Exception {
        when(service.trending(10, null, null)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/stories/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("000000000000000000000001"))
                .andExpect(jsonPath("$[0].reasons[0]").value("RECENTLY_UPDATED"))
                .andExpect(jsonPath("$[0].score").doesNotExist())
                .andExpect(jsonPath("$[0].sourceIds").doesNotExist())
                .andExpect(jsonPath("$[0].articleIds").doesNotExist())
                .andExpect(jsonPath("$[0].matchingVersion").doesNotExist());
    }

    @Test
    void acceptsCustomLimitCategoryAndDisplayLanguage() throws Exception {
        when(service.trending(5, ArticleCategory.LOCAL, Language.SI)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stories/trending")
                        .param("limit", "5")
                        .param("category", "local")
                        .param("displayLanguage", "si"))
                .andExpect(status().isOk());

        verify(service).trending(5, ArticleCategory.LOCAL, Language.SI);
    }

    @Test
    void rejectsLimitOutsideSafeRangeAndInvalidCategory() throws Exception {
        mockMvc.perform(get("/api/v1/stories/trending").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/stories/trending").param("limit", "51"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/stories/trending").param("category", "unknown"))
                .andExpect(status().isBadRequest());
    }

    private TrendingStoryResponse response() {
        return new TrendingStoryResponse(
                "000000000000000000000001", "Recent Story", ArticleCategory.LOCAL,
                Instant.parse("2026-09-02T10:00:00Z"),
                Instant.parse("2026-09-02T11:00:00Z"), 2, 2, null,
                List.of(TrendingReason.RECENTLY_UPDATED, TrendingReason.MULTIPLE_SOURCES));
    }
}
