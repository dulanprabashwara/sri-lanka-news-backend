package lk.srilankannews.story.ask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import lk.srilankannews.ai.GeminiProperties;
import lk.srilankannews.ai.openrouter.OpenRouterProperties;
import lk.srilankannews.processing.enrichment.GeminiRequestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AskStoryProperties.class)
class AskStoryConfiguration {
    @Bean
    @org.springframework.context.annotation.Primary
    GroundedAnswerProvider groundedAnswerProvider(
            Client client,
            GeminiProperties geminiProperties,
            AskStoryProperties properties,
            ObjectMapper objectMapper,
            @Autowired(required = false) OpenRouterGroundedAnswerProvider openRouterProvider,
            @Autowired(required = false) OpenRouterProperties openRouterProperties,
            @Autowired(required = false) GeminiRequestController geminiRequestController) {
        GroundedAnswerPromptFactory promptFactory = new GroundedAnswerPromptFactory(properties);
        GroundedAnswerProvider primary = new GeminiGroundedAnswerProvider(
                client, geminiProperties, properties, objectMapper, promptFactory);
        if (openRouterProvider != null && openRouterProperties != null && openRouterProperties.configured()) {
            return new ResilientGroundedAnswerProvider(
                    primary, openRouterProvider, openRouterProperties, geminiRequestController);
        }
        return primary;
    }
}
