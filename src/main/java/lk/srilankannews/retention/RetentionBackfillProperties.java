package lk.srilankannews.retention;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.retention.backfill")
public record RetentionBackfillProperties(
        boolean enabled,
        boolean apply,
        @Min(value = 1, message = "Backfill batch size must be at least 1")
        @Max(value = 1000, message = "Backfill batch size must not exceed 1000")
        int batchSize
) {
    public RetentionBackfillProperties {
        if (batchSize <= 0) {
            batchSize = 100;
        }
    }

    public static RetentionBackfillProperties defaults() {
        return new RetentionBackfillProperties(false, false, 100);
    }
}
