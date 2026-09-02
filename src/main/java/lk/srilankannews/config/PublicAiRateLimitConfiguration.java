package lk.srilankannews.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PublicAiRateLimitProperties.class)
class PublicAiRateLimitConfiguration {
}
