package lk.srilankannews.translation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;

class AzureTranslationProviderTest {

    @Test
    void sendsConfiguredRegionAndReturnsProviderProvenance() throws Exception {
        AtomicReference<String> region = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/translate", exchange -> {
            region.set(exchange.getRequestHeaders().getFirst("Ocp-Apim-Subscription-Region"));
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("[{\"translations\":[{\"text\":\"සිංහල ශීර්ෂය\",\"to\":\"si\"}]},"
                    + "{\"translations\":[{\"text\":\"සිංහල සාරාංශය\",\"to\":\"si\"}]}]")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var properties = new AzureTranslatorProperties(
                    "test-key", "http://localhost:" + server.getAddress().getPort(),
                    "test-region", Duration.ofSeconds(2), 8000);
            var provider = new AzureTranslationProvider(
                    HttpClient.newHttpClient(), new ObjectMapper(), properties);

            var result = provider.translate(new TranslationInput(
                    Language.EN, "English title", "English summary", Set.of(Language.SI)));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).title()).isEqualTo("සිංහල ශීර්ෂය");
            assertThat(result.get(0).provider()).isEqualTo("AZURE_TRANSLATOR");
            assertThat(region.get()).isEqualTo("test-region");
            var payload = new ObjectMapper().readTree(requestBody.get());
            assertThat(payload.isArray()).isTrue();
            assertThat(payload.size()).isEqualTo(2);
            assertThat(payload.path(0).path("Text").textValue()).isEqualTo("English title");
            assertThat(payload.path(1).path("Text").textValue()).isEqualTo("English summary");
            assertThat(payload.path(0).has("text")).isFalse();
        } finally {
            server.stop(0);
        }
    }
}
