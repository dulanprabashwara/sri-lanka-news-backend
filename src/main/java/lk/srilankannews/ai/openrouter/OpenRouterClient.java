package lk.srilankannews.ai.openrouter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.srilankannews.ai.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenRouterClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenRouterClient.class);
    public static final String PROVIDER = "OPENROUTER";

    private static final Pattern RETRY_AFTER_PATTERN = Pattern.compile(
            "(?i)retry\\s*(?:after|in)\\s*[:=]?\\s*(\\d+)\\s*(s(?:ec(?:ond)?s?)?|m(?:in(?:ute)?s?)?|h(?:our?s?)?)?");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenRouterProperties properties;

    public OpenRouterClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            OpenRouterProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String chatCompletion(String prompt) {
        if (!properties.configured()) {
            throw new AiProviderException(
                    PROVIDER,
                    AiProviderException.Kind.AUTHENTICATION,
                    "OpenRouter is not configured",
                    null,
                    "NOT_CONFIGURED",
                    "OpenRouter API key is missing",
                    properties.model(),
                    null,
                    null);
        }

        try {
            Map<String, Object> requestPayload = Map.of(
                    "model", properties.model(),
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            String jsonBody = objectMapper.writeValueAsString(requestPayload);

            URI uri = URI.create(properties.baseUrl().strip().replaceAll("/+$", "") + "/chat/completions");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(properties.timeout())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + properties.apiKey().strip())
                    .header("HTTP-Referer", "https://ceylonnews.lk")
                    .header("X-Title", "Ceylon News")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw handleHttpError(status, response);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new AiProviderException(
                        PROVIDER,
                        AiProviderException.Kind.INVALID_RESPONSE,
                        "OpenRouter returned no completion choices",
                        status,
                        "EMPTY_CHOICES",
                        "OpenRouter response contained no choices",
                        properties.model(),
                        null,
                        null);
            }

            JsonNode message = choices.get(0).path("message");
            String content = message.path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new AiProviderException(
                        PROVIDER,
                        AiProviderException.Kind.INVALID_RESPONSE,
                        "OpenRouter returned empty message content",
                        status,
                        "EMPTY_CONTENT",
                        "OpenRouter response choice had empty content",
                        properties.model(),
                        null,
                        null);
            }

            return content;
        } catch (AiProviderException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new AiProviderException(
                    PROVIDER,
                    AiProviderException.Kind.TIMEOUT_NETWORK,
                    "OpenRouter request timed out",
                    null,
                    "TIMEOUT",
                    "OpenRouter request timed out",
                    properties.model(),
                    null,
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(
                    PROVIDER,
                    AiProviderException.Kind.TIMEOUT_NETWORK,
                    "OpenRouter request interrupted",
                    null,
                    "INTERRUPTED",
                    "OpenRouter request thread interrupted",
                    properties.model(),
                    null,
                    exception);
        } catch (IOException exception) {
            throw new AiProviderException(
                    PROVIDER,
                    AiProviderException.Kind.PROVIDER_FAILURE,
                    "OpenRouter network request failed",
                    null,
                    "IO_EXCEPTION",
                    exception.getMessage(),
                    properties.model(),
                    null,
                    exception);
        }
    }

    private AiProviderException handleHttpError(int status, HttpResponse<String> response) {
        String body = response.body();
        String errorMsg = null;
        String errorCode = null;
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(body);
                JsonNode errorNode = root.path("error");
                if (!errorNode.isMissingNode()) {
                    errorMsg = errorNode.path("message").asText(null);
                    errorCode = errorNode.path("code").asText(null);
                }
            } catch (Exception ignored) {
            }
        }

        Duration retryAfter = parseRetryAfterHeader(response.headers().firstValue("Retry-After").orElse(null));
        if (retryAfter == null && errorMsg != null) {
            retryAfter = parseRetryAfterFromMessage(errorMsg);
        }

        String combined = ((errorCode == null ? "" : errorCode) + " " + (errorMsg == null ? "" : errorMsg))
                .toUpperCase(Locale.ROOT);
        AiProviderException.Kind kind;
        if (status == 429 || combined.contains("RESOURCE_EXHAUSTED")
                || combined.contains("QUOTA") || combined.contains("RATE LIMIT")) {
            kind = AiProviderException.Kind.RATE_LIMIT;
        } else if (status == 401 || combined.contains("UNAUTHENTICATED")
                || combined.contains("INVALID_API_KEY") || combined.contains("API KEY")) {
            kind = AiProviderException.Kind.AUTHENTICATION;
        } else if (status == 403 || combined.contains("PERMISSION_DENIED")) {
            kind = AiProviderException.Kind.PERMISSION;
        } else if (status == 404 || (combined.contains("MODEL") && combined.contains("NOT FOUND"))) {
            kind = AiProviderException.Kind.MODEL_NOT_FOUND;
        } else if (status == 400 || status == 422 || combined.contains("INVALID_ARGUMENT")) {
            kind = AiProviderException.Kind.INVALID_REQUEST;
        } else if (status >= 500) {
            kind = AiProviderException.Kind.PROVIDER_5XX;
        } else {
            kind = AiProviderException.Kind.PROVIDER_FAILURE;
        }

        return new AiProviderException(
                PROVIDER,
                kind,
                "OpenRouter request failed with HTTP " + status,
                status,
                errorCode != null ? errorCode : "HTTP_" + status,
                errorMsg != null ? errorMsg : "HTTP status " + status,
                properties.model(),
                retryAfter,
                null);
    }

    private Duration parseRetryAfterHeader(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header.strip());
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Duration parseRetryAfterFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = RETRY_AFTER_PATTERN.matcher(message);
        if (matcher.find()) {
            try {
                long amount = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2);
                if (unit != null) {
                    unit = unit.toLowerCase(Locale.ROOT);
                    if (unit.startsWith("m")) {
                        return Duration.ofMinutes(amount);
                    } else if (unit.startsWith("h")) {
                        return Duration.ofHours(amount);
                    }
                }
                return Duration.ofSeconds(amount);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static String extractJson(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }
}
