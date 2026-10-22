/*
 * File overview: Registers Spring components and runtime configuration for Public AI Rate Limit.
 * Area: cross-cutting application configuration.
 */
package lk.srilankannews.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PublicAiRateLimitProperties.class)
class PublicAiRateLimitConfiguration {
}
