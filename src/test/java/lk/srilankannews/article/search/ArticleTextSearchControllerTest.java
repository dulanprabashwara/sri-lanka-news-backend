package lk.srilankannews.article.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.srilankannews.auth.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ArticleTextSearchController.class)
@Import(SecurityConfiguration.class)
class ArticleTextSearchControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ArticleTextSearchService service;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void searchIsGuestAccessible() throws Exception {
        mockMvc.perform(get("/api/v1/search/articles?q=cricket"))
                .andExpect(status().isOk());
        verify(service).search("cricket", 0, 20, null, null, null, null);
    }

    @Test
    void validatesRequiredBlankLongAndPageSizeInputs() throws Exception {
        mockMvc.perform(get("/api/v1/search/articles")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/articles").param("q", "  "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/articles").param("q", "x".repeat(201)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/articles?q=cricket&size=101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forwardsFiltersAndPresentationLanguageSeparately() throws Exception {
        mockMvc.perform(get("/api/v1/search/articles?q=election&category=POLITICS"
                        + "&language=si&source=newsfirst&displayLanguage=en&page=1&size=10"))
                .andExpect(status().isOk());
        verify(service).search(eq("election"), eq(1), eq(10), eq("newsfirst"),
                any(), any(), any());
    }
}
