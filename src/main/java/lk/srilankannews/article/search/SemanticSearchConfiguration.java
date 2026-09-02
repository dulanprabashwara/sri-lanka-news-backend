package lk.srilankannews.article.search;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SemanticSearchProperties.class)
class SemanticSearchConfiguration {
}
