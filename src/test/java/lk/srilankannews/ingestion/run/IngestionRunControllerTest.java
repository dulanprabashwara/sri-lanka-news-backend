package lk.srilankannews.ingestion.run;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import lk.srilankannews.config.IngestionApiKeyAuthenticator;
import lk.srilankannews.config.InvalidIngestionApiKeyException;
import lk.srilankannews.ingestion.run.api.ClaimRequest;
import lk.srilankannews.ingestion.run.api.ClaimResponse;
import lk.srilankannews.ingestion.run.api.CompleteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import lk.srilankannews.common.api.error.GlobalExceptionHandler;

@ExtendWith(MockitoExtension.class)
class IngestionRunControllerTest {

    @Mock
    private IngestionRunService runService;
    @Mock
    private IngestionApiKeyAuthenticator apiKeyAuthenticator;

    @InjectMocks
    private IngestionRunController controller;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mapper.findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void claim_requiresApiKey() throws Exception {
        doThrow(new InvalidIngestionApiKeyException()).when(apiKeyAuthenticator).authenticate(any());

        ClaimRequest request = new ClaimRequest("slug", IngestionTriggerType.SCHEDULED, Instant.now(), "worker");
        mockMvc.perform(post("/api/internal/v1/ingestion-runs/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void claim_returnsClaimResponse() throws Exception {
        ClaimRequest request = new ClaimRequest("slug", IngestionTriggerType.SCHEDULED, Instant.now(), "worker");
        when(runService.claim(any())).thenReturn(ClaimResponse.success("run-1", Instant.now()));

        mockMvc.perform(post("/api/internal/v1/ingestion-runs/claim")
                .header(IngestionApiKeyAuthenticator.HEADER_NAME, "valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(true))
                .andExpect(jsonPath("$.runId").value("run-1"));
        
        verify(apiKeyAuthenticator).authenticate("valid");
    }

    @Test
    void complete_returnsNoContent() throws Exception {
        CompleteRequest request = new CompleteRequest(1, 1, 1, 0);

        mockMvc.perform(post("/api/internal/v1/ingestion-runs/run-1/complete")
                .header(IngestionApiKeyAuthenticator.HEADER_NAME, "valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(runService).complete(anyString(), any());
    }
}
