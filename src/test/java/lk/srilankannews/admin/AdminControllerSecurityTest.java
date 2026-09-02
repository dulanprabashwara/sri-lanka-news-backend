package lk.srilankannews.admin;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import lk.srilankannews.auth.AdminAuthorization;
import lk.srilankannews.auth.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import({SecurityConfiguration.class, AdminAuthorization.class})
@TestPropertySource(properties = "news.admin.user-ids=admin-sub")
class AdminControllerSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean AdminService adminService;

    @Test
    void guestAndForgedHeaderAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/me").header("X-Admin-User", "admin-sub"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedNonAdminAndEmailAloneAreForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me")
                        .with(jwt().jwt(token -> token.subject("reader-sub"))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("admin-sub"))));
        mockMvc.perform(get("/api/v1/admin/me")
                        .with(jwt().jwt(token -> token.subject("reader-sub")
                                .claim("email", "admin-sub"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void configuredVerifiedSubjectCanAccessAllAdminReads() throws Exception {
        when(adminService.sources()).thenReturn(List.of());
        when(adminService.articles(null, null, 25)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/me")
                        .with(jwt().jwt(token -> token.subject("admin-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));
        mockMvc.perform(get("/api/v1/admin/sources")
                        .with(jwt().jwt(token -> token.subject("admin-sub"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/articles")
                        .with(jwt().jwt(token -> token.subject("admin-sub"))))
                .andExpect(status().isOk());
    }

    @Test
    void validatesArticleLimit() throws Exception {
        mockMvc.perform(get("/api/v1/admin/articles?limit=101")
                        .with(jwt().jwt(token -> token.subject("admin-sub"))))
                .andExpect(status().isBadRequest());
    }
}
