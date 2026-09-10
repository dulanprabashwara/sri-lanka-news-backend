package lk.srilankannews.article.trending;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.api.SourceSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import lk.srilankannews.config.LanguageConverter;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TrendingArticleControllerTest {

    @Mock
    private TrendingArticleService trendingArticleService;

    @InjectMocks
    private TrendingArticleController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FormattingConversionService conversionService = new DefaultFormattingConversionService();
        conversionService.addConverter(new LanguageConverter());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setConversionService(conversionService)
                .build();
    }

    @Test
    void getTrendingArticles_withDefaults_returnsArticles() throws Exception {
        ArticleResponse article = new ArticleResponse(
                "art-1",
                "Breaking Trending News",
                "https://example.com/art1",
                Language.EN,
                List.of("Author One"),
                Instant.parse("2026-09-10T12:00:00Z"),
                Instant.parse("2026-09-10T12:05:00Z"),
                ArticleCategory.LOCAL,
                "Summary of news",
                List.of("Sri Lanka"),
                new SourceSummaryResponse("Daily News", "daily-news", "https://example.com")
        );

        when(trendingArticleService.trending(20, null, null))
                .thenReturn(List.of(article));

        mockMvc.perform(get("/api/v1/trending/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("art-1"))
                .andExpect(jsonPath("$[0].title").value("Breaking Trending News"))
                .andExpect(jsonPath("$[0].category").value("LOCAL"))
                .andExpect(jsonPath("$[0].source.name").value("Daily News"));

        verify(trendingArticleService).trending(20, null, null);
    }

    @Test
    void getTrendingArticles_withParameters_passesToService() throws Exception {
        when(trendingArticleService.trending(10, ArticleCategory.POLITICS, Language.SI))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/trending/articles")
                        .param("limit", "10")
                        .param("category", "POLITICS")
                        .param("displayLanguage", "si"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(trendingArticleService).trending(10, ArticleCategory.POLITICS, Language.SI);
    }
}
