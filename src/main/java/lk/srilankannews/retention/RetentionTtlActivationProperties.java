/*
 * File overview: Binds and validates external configuration for Retention TTL Activation.
 * Area: MongoDB and Redis data retention.
 */
package lk.srilankannews.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "news.retention.ttl-activation")
public record RetentionTtlActivationProperties(
        boolean enabled,
        boolean apply,
        int batchSize
) {
    public static RetentionTtlActivationProperties defaults() {
        return new RetentionTtlActivationProperties(false, false, 100);
    }
}
