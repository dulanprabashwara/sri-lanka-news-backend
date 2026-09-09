package lk.srilankannews.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import java.net.http.HttpClient;
import lk.srilankannews.story.StoryEmbeddingProperties;
import lk.srilankannews.translation.AzureTranslationProvider;
import lk.srilankannews.translation.AzureTranslatorProperties;
import lk.srilankannews.translation.GeminiTranslationProvider;
import lk.srilankannews.translation.ResilientTranslationProvider;
import lk.srilankannews.translation.TranslationProperties;
import lk.srilankannews.translation.TranslationProvider;
import lk.srilankannews.translation.TranslationReliabilityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        GeminiProperties.class,
        TranslationProperties.class,
        AzureTranslatorProperties.class,
        TranslationReliabilityProperties.class
})
public class GeminiConfiguration {
    @Bean(destroyMethod = "close")
    Client geminiClient(GeminiProperties properties) {
        return Client.builder()
                .apiKey(properties.apiKey())
                .httpOptions(HttpOptions.builder()
                        .timeout(Math.toIntExact(properties.timeout().toMillis()))
                        .build())
                .build();
    }

    @Bean
    EmbeddingProvider embeddingProvider(
            Client client, StoryEmbeddingProperties properties) {
        return new GeminiEmbeddingProvider(client, properties);
    }

    @Bean
    AiProvider aiProvider(Client client, GeminiProperties properties, ObjectMapper objectMapper) {
        return new GeminiAiProvider(client, properties, objectMapper);
    }

    @Bean
    GeminiTranslationProvider geminiTranslationProvider(
            Client client, TranslationProperties properties, ObjectMapper objectMapper) {
        return new GeminiTranslationProvider(client, properties, objectMapper);
    }

    @Bean
    AzureTranslationProvider azureTranslationProvider(
            AzureTranslatorProperties properties, ObjectMapper objectMapper) {
        return new AzureTranslationProvider(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build(),
                objectMapper,
                properties);
    }

    @Bean
    @Primary
    TranslationProvider translationProvider(
            GeminiTranslationProvider primary,
            AzureTranslationProvider fallback,
            TranslationReliabilityProperties properties) {
        return new ResilientTranslationProvider(primary, fallback, properties);
    }
}
