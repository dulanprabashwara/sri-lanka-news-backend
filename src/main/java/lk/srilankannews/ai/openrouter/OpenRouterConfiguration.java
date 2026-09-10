package lk.srilankannews.ai.openrouter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import lk.srilankannews.story.ask.AskStoryProperties;
import lk.srilankannews.story.ask.GroundedAnswerPromptFactory;
import lk.srilankannews.story.ask.OpenRouterGroundedAnswerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenRouterProperties.class)
public class OpenRouterConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "openRouterHttpClient")
    HttpClient openRouterHttpClient(OpenRouterProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
    }

    @Bean
    OpenRouterClient openRouterClient(
            HttpClient openRouterHttpClient,
            ObjectMapper objectMapper,
            OpenRouterProperties properties) {
        return new OpenRouterClient(openRouterHttpClient, objectMapper, properties);
    }

    @Bean
    OpenRouterAiProvider openRouterAiProvider(
            OpenRouterClient openRouterClient,
            OpenRouterProperties properties,
            ObjectMapper objectMapper) {
        return new OpenRouterAiProvider(openRouterClient, properties, objectMapper);
    }

    @Bean
    OpenRouterGroundedAnswerProvider openRouterGroundedAnswerProvider(
            OpenRouterClient openRouterClient,
            OpenRouterProperties properties,
            AskStoryProperties askStoryProperties,
            ObjectMapper objectMapper) {
        return new OpenRouterGroundedAnswerProvider(
                openRouterClient,
                properties,
                askStoryProperties,
                objectMapper,
                new GroundedAnswerPromptFactory(askStoryProperties));
    }
}
