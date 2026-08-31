package lk.srilankannews.article.cache;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.cache.article-feed")
public record ArticleFeedCacheProperties(
        boolean enabled,
        @NotNull Duration ttl) {
}
