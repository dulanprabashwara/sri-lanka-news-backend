package lk.srilankannews.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import lk.srilankannews.story.StoryEmbeddingProperties;
import lk.srilankannews.translation.GeminiTranslationProvider;
import lk.srilankannews.translation.TranslationProperties;
import lk.srilankannews.translation.TranslationProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({GeminiProperties.class, TranslationProperties.class})
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
    TranslationProvider translationProvider(
            Client client, TranslationProperties properties, ObjectMapper objectMapper) {
        return new GeminiTranslationProvider(client, properties, objectMapper);
    }
}
