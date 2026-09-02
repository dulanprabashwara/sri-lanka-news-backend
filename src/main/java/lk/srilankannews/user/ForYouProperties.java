package lk.srilankannews.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.for-you")
public record ForYouProperties(@Min(1) @Max(2000) int candidateLimit) {
}
