package lk.srilankannews.source.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SourceController.class)
@Import(SourceApiMapper.class)
class SourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SourceService sourceService;

    @Test
    void listsSourcesUsingPublicDtos() throws Exception {
        when(sourceService.findAllByName()).thenReturn(List.of(source()));

        mockMvc.perform(get("/api/v1/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Daily Mirror"))
                .andExpect(jsonPath("$[0].slug").value("daily-mirror"))
                .andExpect(jsonPath("$[0].defaultLanguage").value("en"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].enabled").doesNotExist())
                .andExpect(jsonPath("$[0].ingestionType").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());
    }

    @Test
    void returnsSourceDetailBySlug() throws Exception {
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(source()));

        mockMvc.perform(get("/api/v1/sources/daily-mirror"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("daily-mirror"))
                .andExpect(jsonPath("$.baseUrl").value("https://www.dailymirror.lk"));
    }

    @Test
    void returnsStandardNotFoundErrorForUnknownSlug() throws Exception {
        when(sourceService.findBySlug("unknown-source")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/sources/unknown-source"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Source was not found."))
                .andExpect(jsonPath("$.path").value("/api/v1/sources/unknown-source"));
    }

    @Test
    void rejectsMalformedSourceSlug() throws Exception {
        mockMvc.perform(get("/api/v1/sources/Not Valid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private Source source() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        return new Source(
                "source-1",
                "Daily Mirror",
                "daily-mirror",
                "https://www.dailymirror.lk",
                Language.EN,
                IngestionType.RSS,
                true,
                now,
                now);
    }
}
