/*
 * File overview: Registers Spring components and runtime configuration for Trending Article.
 * Area: article ingestion, storage, discovery, and search.
 */
package lk.srilankannews.article.trending;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TrendingArticleProperties.class)
public class TrendingArticleConfiguration {
}
