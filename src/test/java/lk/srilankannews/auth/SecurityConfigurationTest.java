package lk.srilankannews.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.srilankannews.user.UserPreferencesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CurrentUserController.class)
@Import(SecurityConfiguration.class)
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserPreferencesService userPreferencesService;

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Bearer"))));
    }

    @Test
    void malformedBearerTokenReturnsSafeUnauthorizedResponse() throws Exception {
        org.mockito.Mockito.when(jwtDecoder.decode("not-a-jwt"))
                .thenThrow(new BadJwtException("unsafe decoder detail"));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("not-a-jwt"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("unsafe decoder detail"))));
    }

    @Test
    void meReturnsOnlyStableIdentityAndOptionalEmail() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token
                        .subject("supabase-user-1")
                        .claim("email", "reader@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("supabase-user-1"))
                .andExpect(jsonPath("$.email").value("reader@example.com"))
                .andExpect(jsonPath("$.token").doesNotExist());
        org.mockito.Mockito.verify(userPreferencesService).get("supabase-user-1");
    }

    @Test
    void meHandlesMissingEmail() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject("supabase-user-2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("supabase-user-2"))
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.nullValue()));
    }
}
