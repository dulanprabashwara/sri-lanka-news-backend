package lk.srilankannews.ingestion.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.common.api.error.GlobalExceptionHandler;
import lk.srilankannews.config.IngestionApiKeyAuthenticator;
import lk.srilankannews.config.InvalidIngestionApiKeyException;
import lk.srilankannews.ingestion.settings.IngestionSourceSettings;
import lk.srilankannews.ingestion.settings.IngestionSourceSettingsService;
import lk.srilankannews.ingestion.trigger.IngestionTriggerRequest;
import lk.srilankannews.ingestion.trigger.IngestionTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InternalIngestionConfigControllerTest {

    @Mock
    private IngestionSourceSettingsService settingsService;
    @Mock
    private IngestionTriggerService triggerService;
    @Mock
    private IngestionApiKeyAuthenticator apiKeyAuthenticator;

    @InjectMocks
    private InternalIngestionConfigController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // === API Key Security ===

    @Test
    void getSources_missingApiKey_returns401() throws Exception {
        doThrow(new InvalidIngestionApiKeyException()).when(apiKeyAuthenticator).authenticate(null);

        mockMvc.perform(get("/api/internal/v1/ingestion/sources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSources_wrongApiKey_returns401() throws Exception {
        doThrow(new InvalidIngestionApiKeyException()).when(apiKeyAuthenticator).authenticate("wrong");

        mockMvc.perform(get("/api/internal/v1/ingestion/sources")
                        .header(IngestionApiKeyAuthenticator.HEADER_NAME, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSources_validApiKey_returns200() throws Exception {
        Instant now = Instant.now();
        when(settingsService.findAll()).thenReturn(List.of(
                new IngestionSourceSettings(
                        "id1", "src1", "daily-mirror", true, 10, 120, now, now, "SYSTEM"
                )
        ));

        mockMvc.perform(get("/api/internal/v1/ingestion/sources")
                        .header(IngestionApiKeyAuthenticator.HEADER_NAME, "valid-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceSlug").value("daily-mirror"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].intervalMinutes").value(10))
                .andExpect(jsonPath("$[0].jitterSeconds").value(120));

        verify(apiKeyAuthenticator).authenticate("valid-key");
    }

    @Test
    void claimTrigger_missingApiKey_returns401() throws Exception {
        doThrow(new InvalidIngestionApiKeyException()).when(apiKeyAuthenticator).authenticate(null);

        mockMvc.perform(post("/api/internal/v1/ingestion/triggers/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"w1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void claimTrigger_validKey_noPending_returnsFalse() throws Exception {
        when(triggerService.claimNextPending(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/internal/v1/ingestion/triggers/claim")
                        .header(IngestionApiKeyAuthenticator.HEADER_NAME, "valid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"w1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(false));

        verify(apiKeyAuthenticator).authenticate("valid-key");
    }

    @Test
    void claimTrigger_validKey_hasPending_returnsClaimed() throws Exception {
        Instant now = Instant.now();
        IngestionTriggerRequest claimed = new IngestionTriggerRequest(
                "trigger-1", "src1", "daily-mirror", "admin-sub", now,
                IngestionTriggerRequest.STATUS_CLAIMED, 1, null, now, "w1", null, null, null
        );
        when(triggerService.claimNextPending(anyString())).thenReturn(Optional.of(claimed));

        mockMvc.perform(post("/api/internal/v1/ingestion/triggers/claim")
                        .header(IngestionApiKeyAuthenticator.HEADER_NAME, "valid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"w1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(true))
                .andExpect(jsonPath("$.triggerId").value("trigger-1"))
                .andExpect(jsonPath("$.sourceSlug").value("daily-mirror"));
    }

    @Test
    void triggerRetry_missingApiKey_returns401() throws Exception {
        doThrow(new InvalidIngestionApiKeyException()).when(apiKeyAuthenticator).authenticate(null);

        mockMvc.perform(post("/api/internal/v1/ingestion/triggers/trig-1/retry"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void triggerComplete_missingApiKey_returns401() throws Exception {
        doThrow(new InvalidIngestionApiKeyException()).when(apiKeyAuthenticator).authenticate(null);

        mockMvc.perform(post("/api/internal/v1/ingestion/triggers/trig-1/complete"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void triggerFail_missingApiKey_returns401() throws Exception {
        doThrow(new InvalidIngestionApiKeyException()).when(apiKeyAuthenticator).authenticate(null);

        mockMvc.perform(post("/api/internal/v1/ingestion/triggers/trig-1/fail"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void triggerRunStarted_missingApiKey_returns401() throws Exception {
        doThrow(new InvalidIngestionApiKeyException()).when(apiKeyAuthenticator).authenticate(null);

        mockMvc.perform(post("/api/internal/v1/ingestion/triggers/trig-1/run-started")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runId\":\"run-1\"}"))
                .andExpect(status().isUnauthorized());
    }
}
