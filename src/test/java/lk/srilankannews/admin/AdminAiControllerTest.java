package lk.srilankannews.admin;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import lk.srilankannews.ai.GeminiProperties;
import lk.srilankannews.ai.openrouter.OpenRouterProperties;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.auth.AdminAuthorization;
import lk.srilankannews.auth.SecurityConfiguration;
import lk.srilankannews.story.StoryEmbeddingProperties;
import lk.srilankannews.story.ask.AskStoryProperties;
import lk.srilankannews.translation.AzureTranslatorProperties;
import lk.srilankannews.translation.TranslationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAiController.class)
@Import({SecurityConfiguration.class, AdminAuthorization.class, AdminAiControllerTest.TestConfig.class})
@TestPropertySource(properties = "news.admin.user-ids=admin-sub")
class AdminAiControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GeminiProperties geminiProperties() {
            return new GeminiProperties("mock-gemini-key", "gemini-3.6-flash", "v2", Duration.ofSeconds(30), 10000);
        }

        @Bean
        OpenRouterProperties openRouterProperties() {
            return new OpenRouterProperties("mock-openrouter-key", "deepseek/deepseek-r1:free", "https://openrouter.ai/api/v1", Duration.ofSeconds(30));
        }

        @Bean
        TranslationProperties translationProperties() {
            return new TranslationProperties("gemini-3.6-flash", "v1", 10000, 20);
        }

        @Bean
        AzureTranslatorProperties azureTranslatorProperties() {
            return new AzureTranslatorProperties("mock-azure-key", "https://api.cognitive.microsofttranslator.com", "southeastasia", Duration.ofSeconds(10), 10000);
        }

        @Bean
        StoryEmbeddingProperties storyEmbeddingProperties() {
            return new StoryEmbeddingProperties("gemini-embedding-2", 768, "v1", 1000, 0.85, 0.80, 20);
        }

        @Bean
        AskStoryProperties askStoryProperties() {
            return new AskStoryProperties("v1", 1000, 5, 2000, 10000, 1500);
        }
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean AdminMongoOperations operations;

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai")
                        .with(jwt().jwt(token -> token.subject("regular-user"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessOverviewWithAllProviders() throws Exception {
        when(operations.articleCount(ProcessingStatus.COMPLETED)).thenReturn(42L);
        when(operations.articleCount(ProcessingStatus.FAILED)).thenReturn(2L);
        when(operations.articleCount(ProcessingStatus.RETRYING)).thenReturn(1L);

        mockMvc.perform(get("/api/v1/admin/ai")
                        .with(jwt().jwt(token -> token.subject("admin-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrichment.completed").value(42))
                .andExpect(jsonPath("$.enrichment.failed").value(2))
                .andExpect(jsonPath("$.enrichment.retrying").value(1))
                .andExpect(jsonPath("$.provider.configured").value(true))
                .andExpect(jsonPath("$.provider.providerName").value("Gemini Provider"))
                .andExpect(jsonPath("$.provider.modelName").value("gemini-3.6-flash"))
                .andExpect(jsonPath("$.providers.length()").value(7))
                // Enrichment Primary (Gemini)
                .andExpect(jsonPath("$.providers[0].id").value("gemini-enrichment"))
                .andExpect(jsonPath("$.providers[0].name").value("Google Gemini"))
                .andExpect(jsonPath("$.providers[0].pipeline").value("ENRICHMENT"))
                .andExpect(jsonPath("$.providers[0].role").value("PRIMARY"))
                .andExpect(jsonPath("$.providers[0].model").value("gemini-3.6-flash"))
                .andExpect(jsonPath("$.providers[0].configured").value(true))
                // Enrichment Fallback (OpenRouter)
                .andExpect(jsonPath("$.providers[1].id").value("openrouter-enrichment"))
                .andExpect(jsonPath("$.providers[1].name").value("OpenRouter AI"))
                .andExpect(jsonPath("$.providers[1].pipeline").value("ENRICHMENT"))
                .andExpect(jsonPath("$.providers[1].role").value("FALLBACK"))
                .andExpect(jsonPath("$.providers[1].model").value("deepseek/deepseek-r1:free"))
                .andExpect(jsonPath("$.providers[1].configured").value(true))
                // Translation Primary (Gemini)
                .andExpect(jsonPath("$.providers[2].id").value("gemini-translation"))
                .andExpect(jsonPath("$.providers[2].name").value("Google Gemini Multilingual"))
                .andExpect(jsonPath("$.providers[2].pipeline").value("TRANSLATION"))
                .andExpect(jsonPath("$.providers[2].role").value("PRIMARY"))
                .andExpect(jsonPath("$.providers[2].model").value("gemini-3.6-flash"))
                .andExpect(jsonPath("$.providers[2].configured").value(true))
                // Translation Fallback (Azure)
                .andExpect(jsonPath("$.providers[3].id").value("azure-translation"))
                .andExpect(jsonPath("$.providers[3].name").value("Microsoft Azure AI Translator"))
                .andExpect(jsonPath("$.providers[3].pipeline").value("TRANSLATION"))
                .andExpect(jsonPath("$.providers[3].role").value("FALLBACK"))
                .andExpect(jsonPath("$.providers[3].model").value("text-translation-v3"))
                .andExpect(jsonPath("$.providers[3].configured").value(true))
                .andExpect(jsonPath("$.providers[3].details").value("Region: southeastasia"))
                // Embeddings
                .andExpect(jsonPath("$.providers[4].id").value("gemini-embedding"))
                .andExpect(jsonPath("$.providers[4].pipeline").value("EMBEDDING"))
                .andExpect(jsonPath("$.providers[4].configured").value(true))
                // Grounded Q&A Primary
                .andExpect(jsonPath("$.providers[5].id").value("gemini-ask-story"))
                .andExpect(jsonPath("$.providers[5].pipeline").value("GROUNDED_QA"))
                .andExpect(jsonPath("$.providers[5].role").value("PRIMARY"))
                // Grounded Q&A Fallback
                .andExpect(jsonPath("$.providers[6].id").value("openrouter-ask-story"))
                .andExpect(jsonPath("$.providers[6].pipeline").value("GROUNDED_QA"))
                .andExpect(jsonPath("$.providers[6].role").value("FALLBACK"));
    }
}
