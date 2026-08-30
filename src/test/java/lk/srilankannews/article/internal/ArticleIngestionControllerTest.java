package lk.srilankannews.article.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.srilankannews.config.IngestionApiKeyAuthenticator;
import lk.srilankannews.config.InvalidIngestionApiKeyException;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ArticleIngestionController.class)
class ArticleIngestionControllerTest {

    private static final String ENDPOINT = "/api/internal/v1/articles";
    private static final String API_KEY = "test-ingestion-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleIngestionService articleIngestionService;

    @MockitoBean
    private IngestionApiKeyAuthenticator apiKeyAuthenticator;

    @Test
    void returnsCreatedForNewArticle() throws Exception {
        when(articleIngestionService.ingest(any())).thenReturn(new ArticleIngestionResponse(
                ArticleIngestionResponse.Status.CREATED,
                "507f1f77bcf86cd799439011",
                canonicalUrl(),
                null));

        mockMvc.perform(post(ENDPOINT)
                        .header(IngestionApiKeyAuthenticator.HEADER_NAME, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.articleId").value("507f1f77bcf86cd799439011"))
                .andExpect(jsonPath("$.canonicalUrl").value(canonicalUrl()));

        verify(apiKeyAuthenticator).authenticate(API_KEY);
    }

    @Test
    void returnsOkForDuplicateArticle() throws Exception {
        when(articleIngestionService.ingest(any())).thenReturn(new ArticleIngestionResponse(
                ArticleIngestionResponse.Status.DUPLICATE,
                "507f1f77bcf86cd799439011",
                canonicalUrl(),
                ArticleIngestionResponse.DuplicateReason.CONTENT_DUPLICATE));

        mockMvc.perform(post(ENDPOINT)
                        .header(IngestionApiKeyAuthenticator.HEADER_NAME, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.duplicateReason").value("CONTENT_DUPLICATE"));
    }

    @Test
    void rejectsInvalidApiKeyUsingCentralErrorFormat() throws Exception {
        doThrow(new InvalidIngestionApiKeyException())
                .when(apiKeyAuthenticator).authenticate("wrong-key");

        mockMvc.perform(post(ENDPOINT)
                        .header(IngestionApiKeyAuthenticator.HEADER_NAME, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("A valid ingestion API key is required."));
    }

    @Test
    void validatesNormalizedIngestionPayload() throws Exception {
        String invalidJson = validJson().replace("\"title\": \"Fixture story\"",
                "\"title\": \"\"");

        mockMvc.perform(post(ENDPOINT)
                        .header(IngestionApiKeyAuthenticator.HEADER_NAME, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("title"));
    }

    @Test
    void returnsNotFoundForUnknownSource() throws Exception {
        when(articleIngestionService.ingest(any()))
                .thenThrow(new ResourceNotFoundException("Source"));

        mockMvc.perform(post(ENDPOINT)
                        .header(IngestionApiKeyAuthenticator.HEADER_NAME, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Source was not found."));
    }

    @Test
    void rejectsExtractedContentAboveMaximumSize() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(IngestionApiKeyAuthenticator.HEADER_NAME, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("Clean fixture body", "x".repeat(500_001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("extractedContent"));
    }

    private String validJson() {
        return """
                {
                  "sourceSlug": "daily-mirror",
                  "title": "Fixture story",
                  "originalUrl": "%s?utm_source=rss",
                  "canonicalUrl": "%s",
                  "originalLanguage": "en",
                  "authors": ["DM Editorial"],
                  "publishedAt": "2026-08-30T05:19:00Z",
                  "discoveredAt": "2026-08-30T05:20:00Z",
                  "category": "LOCAL",
                  "extractedContent": "Clean fixture body"
                }
                """.formatted(canonicalUrl(), canonicalUrl());
    }

    private String canonicalUrl() {
        return "https://www.dailymirror.lk/breaking-news/Fixture-story/108-123456";
    }
}
