/*
 * File overview: Registers Spring components and runtime configuration for Semantic Search.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article.search;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SemanticSearchProperties.class)
class SemanticSearchConfiguration {
}
