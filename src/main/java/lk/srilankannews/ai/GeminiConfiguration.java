package lk.srilankannews.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeminiProperties.class)
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
    AiProvider aiProvider(Client client, GeminiProperties properties, ObjectMapper objectMapper) {
        return new GeminiAiProvider(client, properties, objectMapper);
    }
}
