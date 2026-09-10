package lk.srilankannews.ai.openrouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import lk.srilankannews.ai.AiProviderException;
import org.junit.jupiter.api.Test;

class OpenRouterClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsAuthAndParsesContentSuccessfully() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "id": "gen-123",
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "{\\"answer\\": \\"Hello World\\"}"
                          }
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            OpenRouterProperties properties = new OpenRouterProperties(
                    "secret-key", "openrouter/free", "http://localhost:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2));
            OpenRouterClient client = new OpenRouterClient(
                    HttpClient.newHttpClient(), objectMapper, properties);

            String content = client.chatCompletion("Test prompt");

            assertThat(authHeader.get()).isEqualTo("Bearer secret-key");
            assertThat(content).isEqualTo("{\"answer\": \"Hello World\"}");
            assertThat(requestBody.get()).contains("openrouter/free");
            assertThat(requestBody.get()).contains("Test prompt");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractJsonStripsFencesAndSurroundingText() {
        assertThat(OpenRouterClient.extractJson("{\"a\": 1}")).isEqualTo("{\"a\": 1}");

        String fenced = "```json\n{\"summary\": \"Sri Lanka news\"}\n```";
        assertThat(OpenRouterClient.extractJson(fenced)).isEqualTo("{\"summary\": \"Sri Lanka news\"}");

        String withCommentary = "Here is the result:\n```json\n{\"test\": true}\n```\nHope that helps!";
        assertThat(OpenRouterClient.extractJson(withCommentary)).isEqualTo("{\"test\": true}");
    }

    @Test
    void maps429RateLimitWithRetryAfter() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] response = "{\"error\": {\"message\": \"Rate limit exceeded\", \"code\": 429}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Retry-After", "45");
            exchange.sendResponseHeaders(429, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            OpenRouterProperties properties = new OpenRouterProperties(
                    "secret-key", "openrouter/free", "http://localhost:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2));
            OpenRouterClient client = new OpenRouterClient(
                    HttpClient.newHttpClient(), objectMapper, properties);

            assertThatThrownBy(() -> client.chatCompletion("Prompt"))
                    .isInstanceOf(AiProviderException.class)
                    .satisfies(ex -> {
                        AiProviderException ape = (AiProviderException) ex;
                        assertThat(ape.kind()).isEqualTo(AiProviderException.Kind.RATE_LIMIT);
                        assertThat(ape.httpStatus()).isEqualTo(429);
                        assertThat(ape.retryAfter()).isEqualTo(Duration.ofSeconds(45));
                        assertThat(ape.provider()).isEqualTo("OPENROUTER");
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void maps401AuthenticationError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] response = "{\"error\": {\"message\": \"Invalid API key\", \"code\": 401}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            OpenRouterProperties properties = new OpenRouterProperties(
                    "invalid-key", "openrouter/free", "http://localhost:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2));
            OpenRouterClient client = new OpenRouterClient(
                    HttpClient.newHttpClient(), objectMapper, properties);

            assertThatThrownBy(() -> client.chatCompletion("Prompt"))
                    .isInstanceOf(AiProviderException.class)
                    .satisfies(ex -> {
                        AiProviderException ape = (AiProviderException) ex;
                        assertThat(ape.kind()).isEqualTo(AiProviderException.Kind.AUTHENTICATION);
                        assertThat(ape.httpStatus()).isEqualTo(401);
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void maps500ServerError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] response = "{\"error\": {\"message\": \"Internal Server Error\", \"code\": 500}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            OpenRouterProperties properties = new OpenRouterProperties(
                    "secret-key", "openrouter/free", "http://localhost:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2));
            OpenRouterClient client = new OpenRouterClient(
                    HttpClient.newHttpClient(), objectMapper, properties);

            assertThatThrownBy(() -> client.chatCompletion("Prompt"))
                    .isInstanceOf(AiProviderException.class)
                    .satisfies(ex -> {
                        AiProviderException ape = (AiProviderException) ex;
                        assertThat(ape.kind()).isEqualTo(AiProviderException.Kind.PROVIDER_5XX);
                        assertThat(ape.httpStatus()).isEqualTo(500);
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void throwsWhenNotConfigured() {
        OpenRouterProperties properties = new OpenRouterProperties(
                "", "openrouter/free", "https://openrouter.ai/api/v1", Duration.ofSeconds(2));
        OpenRouterClient client = new OpenRouterClient(
                HttpClient.newHttpClient(), objectMapper, properties);

        assertThatThrownBy(() -> client.chatCompletion("Prompt"))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> {
                    AiProviderException ape = (AiProviderException) ex;
                    assertThat(ape.kind()).isEqualTo(AiProviderException.Kind.AUTHENTICATION);
                    assertThat(ape.providerCode()).isEqualTo("NOT_CONFIGURED");
                });
    }
}
