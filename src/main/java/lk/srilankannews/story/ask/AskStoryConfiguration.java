package lk.srilankannews.story.ask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import lk.srilankannews.ai.GeminiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AskStoryProperties.class)
class AskStoryConfiguration {
    @Bean
    GroundedAnswerProvider groundedAnswerProvider(
            Client client, GeminiProperties geminiProperties, AskStoryProperties properties,
            ObjectMapper objectMapper) {
        return new GeminiGroundedAnswerProvider(
                client, geminiProperties, properties, objectMapper,
                new GroundedAnswerPromptFactory(properties));
    }
}
