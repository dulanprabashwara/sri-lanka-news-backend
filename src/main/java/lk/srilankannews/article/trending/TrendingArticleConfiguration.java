package lk.srilankannews.article.trending;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TrendingArticleProperties.class)
public class TrendingArticleConfiguration {
}
