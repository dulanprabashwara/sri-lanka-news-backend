package lk.srilankannews.article.trending;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.article.trending")
public record TrendingArticleProperties(
        @DefaultValue("48h") Duration lookbackWindow,
        @DefaultValue("8h") Duration halfLife,
        @DefaultValue("200") @Min(10) @Max(1000) int candidateLimit,
        @DefaultValue("20") @Min(1) @Max(50) int defaultLimit,
        @DefaultValue("50") @Min(1) @Max(100) int maxLimit,
        @DefaultValue("2") @Min(1) @Max(10) int maxPerStory,
        @DefaultValue("3") @Min(1) @Max(10) int maxPerSource,
        @DefaultValue("90s") Duration cacheTtl
) {
    public TrendingArticleProperties {
        lookbackWindow = lookbackWindow == null ? Duration.ofHours(48) : lookbackWindow;
        halfLife = halfLife == null ? Duration.ofHours(8) : halfLife;
        candidateLimit = candidateLimit <= 0 ? 200 : candidateLimit;
        defaultLimit = defaultLimit <= 0 ? 20 : defaultLimit;
        maxLimit = maxLimit <= 0 ? 50 : maxLimit;
        maxPerStory = maxPerStory <= 0 ? 2 : maxPerStory;
        maxPerSource = maxPerSource <= 0 ? 3 : maxPerSource;
        cacheTtl = cacheTtl == null ? Duration.ofSeconds(90) : cacheTtl;
    }
}
