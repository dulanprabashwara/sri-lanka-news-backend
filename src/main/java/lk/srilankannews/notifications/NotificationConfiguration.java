package lk.srilankannews.notifications;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        NotificationRedisProperties.class,
        PendingNotificationRecoveryProperties.class
})
public class NotificationConfiguration {
}

