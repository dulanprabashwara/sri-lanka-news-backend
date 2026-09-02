package lk.srilankannews.common.api.error;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lk.srilankannews.config.RequestCorrelationFilter;
import lk.srilankannews.auth.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerWebTest.TestController.class)
@ContextConfiguration(classes = {
        GlobalExceptionHandlerWebTest.TestController.class,
        GlobalExceptionHandler.class,
        RequestCorrelationFilter.class,
        SecurityConfiguration.class
})
class GlobalExceptionHandlerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void formatsRequestBodyValidationErrors() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.path").value("/test/validation"))
                .andExpect(jsonPath("$.requestId", not(blankOrNullString())))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].code").value("NotBlank"));
    }

    @Test
    void formatsMethodArgumentValidationErrors() throws Exception {
        mockMvc.perform(get("/test/method-validation").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].message")
                        .value("must be greater than or equal to 1"));
    }

    @Test
    void formatsMalformedJsonWithoutParserDetails() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("Request body is malformed or unreadable."))
                .andExpect(jsonPath("$.details", empty()));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }

    @Test
    void preservesSafeIncomingRequestId() throws Exception {
        mockMvc.perform(get("/test/ok")
                        .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "client-request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        RequestCorrelationFilter.REQUEST_ID_HEADER,
                        "client-request-123"));
    }

    @Test
    void replacesUnsafeIncomingRequestId() throws Exception {
        mockMvc.perform(get("/test/ok")
                        .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "unsafe request id"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        RequestCorrelationFilter.REQUEST_ID_HEADER,
                        not("unsafe request id")));
    }

    @RestController
    @Validated
    public static class TestController {

        @PostMapping("/test/validation")
        void validateBody(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/method-validation")
        void validateMethodArgument(@RequestParam @Min(1) int limit) {
        }

        @GetMapping("/test/unexpected")
        void throwUnexpectedException() {
            throw new IllegalStateException("sensitive implementation detail");
        }

        @GetMapping("/test/ok")
        void ok() {
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
