package lk.srilankannews.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lk.srilankannews.auth.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@WebMvcTest({UserPreferencesController.class, UserBookmarkController.class,
        UserFollowController.class, ForYouController.class})
@Import(SecurityConfiguration.class)
class UserApiSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean UserPreferencesService preferencesService;
    @MockitoBean UserBookmarkService bookmarkService;
    @MockitoBean UserFollowService followService;
    @MockitoBean ForYouService forYouService;

    @Test
    void preferencesAndBookmarksRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me/preferences")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/bookmarks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/follows")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/for-you")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/me/follows/sources/daily-mirror/seen")).andExpect(status().isUnauthorized());
    }

    @Test
    void markSourceSeenRequiresAuthenticationAndExtractsSubject() throws Exception {
        mockMvc.perform(post("/api/v1/me/follows/sources/daily-mirror/seen")
                        .with(jwt().jwt(token -> token.subject("user-a"))))
                .andExpect(status().isNoContent());
        verify(followService).markSourceSeen("user-a", "daily-mirror");
    }

    @Test
    void followOwnerAlwaysComesFromJwtSubject() throws Exception {
        mockMvc.perform(post("/api/v1/me/follows/topics")
                        .with(jwt().jwt(token -> token.subject("user-a")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"user-b\",\"topic\":\"Cricket\"}"))
                .andExpect(status().isOk());
        verify(followService).followTopic("user-a", "Cricket");
    }

    @Test
    void validatesTopicAndBatchBounds() throws Exception {
        mockMvc.perform(post("/api/v1/me/follows/topics")
                        .with(jwt().jwt(token -> token.subject("user-a")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"   \"}"))
                .andExpect(status().isBadRequest());

        String oversizedBatch = objectMapper.writeValueAsString(Map.of(
                "sourceSlugs", List.of(), "topics", Collections.nCopies(21, "Cricket")));
        mockMvc.perform(post("/api/v1/me/follows/status")
                        .with(jwt().jwt(token -> token.subject("user-a")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedBatch))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticatedSubjectOwnsAllQueries() throws Exception {
        mockMvc.perform(get("/api/v1/me/preferences")
                        .with(jwt().jwt(token -> token.subject("user-a"))))
                .andExpect(status().isOk());
        verify(preferencesService).get("user-a");

        mockMvc.perform(get("/api/v1/me/for-you")
                        .with(jwt().jwt(token -> token.subject("user-a"))))
                .andExpect(status().isOk());
        verify(forYouService).feed("user-a", 0, 20, null);
    }

    @Test
    void forYouPaginationIsBounded() throws Exception {
        mockMvc.perform(get("/api/v1/me/for-you?size=101")
                        .with(jwt().jwt(token -> token.subject("user-a"))))
                .andExpect(status().isBadRequest());
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
