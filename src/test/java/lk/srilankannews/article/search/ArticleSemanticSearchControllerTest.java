package lk.srilankannews.article.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.srilankannews.auth.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ArticleSemanticSearchController.class)
@Import(SecurityConfiguration.class)
class ArticleSemanticSearchControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ArticleSemanticSearchService service;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void semanticSearchIsGuestAccessibleAndForwardsParameters() throws Exception {
        when(service.search(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenReturn(SemanticSearchResponse.empty("road accident", 0, 20));

        mockMvc.perform(get("/api/v1/search/semantic")
                        .param("q", "road accident")
                        .param("source", "newsfirst")
                        .param("category", "LOCAL")
                        .param("language", "si")
                        .param("displayLanguage", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("road accident"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.vectorSearchScore").doesNotExist())
                .andExpect(jsonPath("$.semanticEmbedding").doesNotExist());
        verify(service).search("road accident", 0, 20, "newsfirst",
                lk.srilankannews.article.ArticleCategory.LOCAL,
                lk.srilankannews.common.domain.Language.SI,
                lk.srilankannews.common.domain.Language.EN);
    }

    @Test
    void validatesQueryPageAndSize() throws Exception {
        mockMvc.perform(get("/api/v1/search/semantic")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/semantic").param("q", "  "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/semantic").param("q", "x".repeat(201)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/semantic?q=cricket&size=101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsSafe503WhenProviderOrAtlasVectorSearchIsUnavailable() throws Exception {
        when(service.search(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenThrow(new SemanticSearchUnavailableException(
                        new RuntimeException("private provider response")));

        mockMvc.perform(get("/api/v1/search/semantic?q=cricket"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SEMANTIC_SEARCH_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("Semantic search is temporarily unavailable."))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
    }
}
