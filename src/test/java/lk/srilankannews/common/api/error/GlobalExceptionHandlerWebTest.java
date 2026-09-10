package lk.srilankannews.common.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.config.RequestCorrelationFilter;
import lk.srilankannews.auth.SecurityConfiguration;
import lk.srilankannews.story.ask.AskStoryUnavailableException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

    @Test
    void logsAiProviderRateLimitDiagnosticsAndReturnsUnavailable() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            mockMvc.perform(get("/test/ask-story-rate-limit")
                            .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "req-rate-limit-123"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("ASK_STORY_UNAVAILABLE"))
                    .andExpect(jsonPath("$.message").value("Ask This Story is temporarily unavailable."))
                    .andExpect(jsonPath("$.requestId").value("req-rate-limit-123"))
                    .andExpect(jsonPath("$.details", empty()));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String logged = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + " " + right);

        assertThat(logged).contains("Ask This Story unavailable");
        assertThat(logged).contains("cause=AiProviderException");
        assertThat(logged).contains("provider=GEMINI");
        assertThat(logged).contains("category=RATE_LIMIT");
        assertThat(logged).contains("status=429");
        assertThat(logged).contains("error=\"Resource has been exhausted (e.g. check quota).\"");
        assertThat(logged).contains("requestId=req-rate-limit-123");
    }

    @Test
    void logsAiProviderTimeoutDiagnosticsWithoutStatusAndReturnsUnavailable() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            mockMvc.perform(get("/test/ask-story-timeout")
                            .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "req-timeout-456"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("ASK_STORY_UNAVAILABLE"))
                    .andExpect(jsonPath("$.message").value("Ask This Story is temporarily unavailable."))
                    .andExpect(jsonPath("$.requestId").value("req-timeout-456"))
                    .andExpect(jsonPath("$.details", empty()));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String logged = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + " " + right);

        assertThat(logged).contains("Ask This Story unavailable");
        assertThat(logged).contains("cause=AiProviderException");
        assertThat(logged).contains("provider=GEMINI");
        assertThat(logged).contains("category=TIMEOUT_NETWORK");
        assertThat(logged).doesNotContain("status=");
        assertThat(logged).contains("error=\"Gemini network request failed\"");
        assertThat(logged).contains("requestId=req-timeout-456");
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

        @GetMapping("/test/ask-story-rate-limit")
        void throwAskStoryRateLimit() {
            throw new AskStoryUnavailableException(
                    new AiProviderException(
                            AiProviderException.Kind.RATE_LIMIT,
                            "Gemini request failed",
                            429,
                            "RESOURCE_EXHAUSTED",
                            "Resource has been exhausted (e.g. check quota).",
                            "gemini-2.5-flash",
                            null));
        }

        @GetMapping("/test/ask-story-timeout")
        void throwAskStoryTimeout() {
            throw new AskStoryUnavailableException(
                    new AiProviderException(
                            AiProviderException.Kind.TIMEOUT_NETWORK,
                            "Gemini request failed",
                            null,
                            "NETWORK_OR_TIMEOUT",
                            "Gemini network request failed",
                            "gemini-2.5-flash",
                            null));
        }

        @GetMapping("/test/ok")
        void ok() {
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
