package lk.srilankannews.story;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        StoryClusteringProperties.class,
        StoryEmbeddingProperties.class,
        TrendingProperties.class
})
class StoryConfiguration {
}
