package lk.srilankannews.translation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lk.srilankannews.common.domain.Language;

public class AzureTranslationProvider implements TranslationProvider {
    static final String PROVIDER = "AZURE_TRANSLATOR";
    static final String MODEL = "text-translation-v3";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AzureTranslatorProperties properties;

    public AzureTranslationProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AzureTranslatorProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<TranslatedContent> translate(TranslationInput input) {
        if (!properties.configured()) {
            throw new TranslationProviderException(
                    TranslationProviderException.Kind.AUTHENTICATION,
                    "Azure Translator is not configured.");
        }
        List<TranslatedContent> translations = new ArrayList<>();
        for (Language target : input.targetLanguages().stream().sorted().toList()) {
            translations.add(translateTarget(input, target));
        }
        return List.copyOf(translations);
    }

    private TranslatedContent translateTarget(TranslationInput input, Language target) {
        List<TextRequest> body = new ArrayList<>();
        body.add(new TextRequest(input.title()));
        boolean hasSummary = input.summary() != null && !input.summary().isBlank();
        if (hasSummary) {
            body.add(new TextRequest(input.summary()));
        }
        int characters = body.stream().mapToInt(item -> item.text().length()).sum();
        if (characters == 0 || characters > properties.maxInputCharacters()) {
            throw new TranslationProviderException(
                    TranslationProviderException.Kind.VALIDATION,
                    "Azure translation input is invalid.");
        }

        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(input.sourceLanguage(), target))
                    .timeout(properties.timeout())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Ocp-Apim-Subscription-Key", properties.key())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (properties.region() != null && !properties.region().isBlank()) {
                request.header("Ocp-Apim-Subscription-Region", properties.region());
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw responseFailure(response.statusCode());
            }
            JsonNode payload = objectMapper.readTree(response.body());
            String title = translatedText(payload, 0);
            String summary = hasSummary ? translatedText(payload, 1) : null;
            return new TranslatedContent(target, title, summary, PROVIDER, MODEL);
        } catch (TranslationProviderException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new TranslationProviderException(
                    TranslationProviderException.Kind.TIMEOUT_NETWORK,
                    "Azure Translator request timed out.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TranslationProviderException(
                    TranslationProviderException.Kind.TIMEOUT_NETWORK,
                    "Azure Translator request was interrupted.", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new TranslationProviderException(
                    TranslationProviderException.Kind.PROVIDER_FAILURE,
                    "Azure Translator request failed.", exception);
        }
    }

    private URI uri(Language source, Language target) {
        String endpoint = properties.endpoint().strip().replaceAll("/+$", "");
        String query = "api-version=3.0&from=" + encode(source.code()) + "&to=" + encode(target.code());
        return URI.create(endpoint + "/translate?" + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String translatedText(JsonNode payload, int index) {
        JsonNode value = payload.path(index).path("translations").path(0).path("text");
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new TranslationProviderException(
                    TranslationProviderException.Kind.INVALID_RESPONSE,
                    "Azure Translator returned an empty or malformed response.");
        }
        return value.textValue();
    }

    private TranslationProviderException responseFailure(int status) {
        TranslationProviderException.Kind kind = switch (status) {
            case 401 -> TranslationProviderException.Kind.AUTHENTICATION;
            case 403 -> TranslationProviderException.Kind.PERMISSION;
            case 429 -> TranslationProviderException.Kind.RATE_LIMIT;
            default -> status >= 500
                    ? TranslationProviderException.Kind.PROVIDER_5XX
                    : TranslationProviderException.Kind.PROVIDER_FAILURE;
        };
        return new TranslationProviderException(kind, "Azure Translator returned HTTP " + status + ".");
    }

    private record TextRequest(@JsonProperty("Text") String text) {
    }
}
