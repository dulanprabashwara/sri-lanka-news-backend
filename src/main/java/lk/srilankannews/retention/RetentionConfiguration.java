package lk.srilankannews.retention;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RetentionProperties.class, RetentionBackfillProperties.class})
public class RetentionConfiguration {
}
