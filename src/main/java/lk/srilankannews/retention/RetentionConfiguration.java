package lk.srilankannews.retention;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RetentionProperties.class, RetentionBackfillProperties.class, RetentionTtlActivationProperties.class, RetentionRedisProperties.class})
public class RetentionConfiguration {
}
