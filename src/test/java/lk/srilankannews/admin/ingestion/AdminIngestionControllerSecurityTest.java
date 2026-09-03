package lk.srilankannews.admin.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lk.srilankannews.admin.audit.AdminAuditEventService;
import lk.srilankannews.auth.AdminAuthorization;
import lk.srilankannews.auth.SecurityConfiguration;
import lk.srilankannews.ingestion.run.IngestionRunRepository;
import lk.srilankannews.ingestion.settings.IngestionSourceSettingsService;
import lk.srilankannews.ingestion.trigger.IngestionTriggerService;
import lk.srilankannews.source.SourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminIngestionController.class)
@Import({SecurityConfiguration.class, AdminAuthorization.class})
@TestPropertySource(properties = "news.admin.user-ids=admin-sub")
class AdminIngestionControllerSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean SourceService sourceService;
    @MockitoBean IngestionSourceSettingsService settingsService;
    @MockitoBean IngestionRunRepository runRepository;
    @MockitoBean IngestionTriggerService triggerService;
    @MockitoBean AdminAuditEventService auditService;

    // === Admin Authorization: Guest / User / Admin ===

    @Test
    void guestReturns401ForSources() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion/sources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestReturns401ForRuns() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion/runs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestReturns401ForRunDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion/runs/some-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestReturns401ForSettingsUpdate() throws Exception {
        mockMvc.perform(put("/api/v1/admin/ingestion/sources/daily-mirror/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"intervalMinutes\":10,\"jitterSeconds\":120}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestReturns401ForManualTrigger() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ingestion/sources/daily-mirror/trigger"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedNonAdminReturns403ForSources() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion/sources")
                        .with(jwt().jwt(token -> token.subject("reader-sub"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedNonAdminReturns403ForRuns() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion/runs")
                        .with(jwt().jwt(token -> token.subject("reader-sub"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedNonAdminReturns403ForRunDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion/runs/some-id")
                        .with(jwt().jwt(token -> token.subject("reader-sub"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedNonAdminReturns403ForSettingsUpdate() throws Exception {
        mockMvc.perform(put("/api/v1/admin/ingestion/sources/daily-mirror/settings")
                        .with(jwt().jwt(token -> token.subject("reader-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"intervalMinutes\":10,\"jitterSeconds\":120}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedNonAdminReturns403ForManualTrigger() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ingestion/sources/daily-mirror/trigger")
                        .with(jwt().jwt(token -> token.subject("reader-sub"))))
                .andExpect(status().isForbidden());
    }

    // === Admin Success ===

    @Test
    void adminCanGetSources() throws Exception {
        when(sourceService.findAllByName()).thenReturn(List.of());
        when(settingsService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/ingestion/sources")
                        .with(jwt().jwt(token -> token.subject("admin-sub"))))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanGetRuns() throws Exception {
        when(runRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/v1/admin/ingestion/runs")
                        .with(jwt().jwt(token -> token.subject("admin-sub"))))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanTriggerManualRun() throws Exception {
        when(triggerService.requestManualTrigger(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/v1/admin/ingestion/sources/daily-mirror/trigger")
                        .with(jwt().jwt(token -> token.subject("admin-sub"))))
                .andExpect(status().isAccepted());
    }

    @Test
    void duplicateManualTriggerReturnsConflict() throws Exception {
        when(triggerService.requestManualTrigger(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/admin/ingestion/sources/daily-mirror/trigger")
                        .with(jwt().jwt(token -> token.subject("admin-sub"))))
                .andExpect(status().isConflict());
    }
}
