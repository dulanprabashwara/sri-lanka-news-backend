/*
 * File overview: Registers Spring components and runtime configuration for Notification.
 * Area: in-app and email notifications.
 */
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

