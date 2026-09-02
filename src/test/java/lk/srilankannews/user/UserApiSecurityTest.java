package lk.srilankannews.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.srilankannews.auth.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@WebMvcTest({UserPreferencesController.class, UserBookmarkController.class})
@Import(SecurityConfiguration.class)
class UserApiSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean UserPreferencesService preferencesService;
    @MockitoBean UserBookmarkService bookmarkService;

    @Test
    void preferencesAndBookmarksRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me/preferences")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/bookmarks")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedSubjectOwnsAllQueries() throws Exception {
        mockMvc.perform(get("/api/v1/me/preferences")
                        .with(jwt().jwt(token -> token.subject("user-a"))))
                .andExpect(status().isOk());
        verify(preferencesService).get("user-a");
    }

    @Test
    void requestCannotOverrideAuthenticatedOwner() throws Exception {
        mockMvc.perform(put("/api/v1/me/preferences")
                        .with(jwt().jwt(token -> token.subject("user-a")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-b","preferredDisplayLanguage":"SI",
                                 "preferredCategories":["SPORTS","SPORTS"]}
                                """))
                .andExpect(status().isOk());
        verify(preferencesService).update(eq("user-a"), any(UserPreferencesRequest.class));

        mockMvc.perform(post("/api/v1/me/bookmarks/articles/article-1")
                        .with(jwt().jwt(token -> token.subject("user-a"))))
                .andExpect(status().isOk());
        verify(bookmarkService).create("user-a", BookmarkTargetType.ARTICLE, "article-1");
    }

    @Test
    void rejectsInvalidPreferenceEnums() throws Exception {
        mockMvc.perform(put("/api/v1/me/preferences")
                        .with(jwt().jwt(token -> token.subject("user-a")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredDisplayLanguage\":\"XX\",\"preferredCategories\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/me/preferences")
                        .with(jwt().jwt(token -> token.subject("user-a")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredDisplayLanguage\":\"EN\",\"preferredCategories\":[\"NOT_A_CATEGORY\"]}"))
                .andExpect(status().isBadRequest());
    }
}
