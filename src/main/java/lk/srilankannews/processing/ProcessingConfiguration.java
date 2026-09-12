package lk.srilankannews.processing;

import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import lk.srilankannews.processing.enrichment.EnrichmentRetryProperties;
import lk.srilankannews.processing.enrichment.GeminiBackgroundProperties;

@Configuration
@EnableConfigurationProperties({
        RedisProcessingProperties.class,
        EnrichmentRetryProperties.class,
        GeminiBackgroundProperties.class,
        PendingArticleRecoveryProperties.class
})
public class ProcessingConfiguration {

    @Bean
    ThreadPoolTaskExecutor articleEventDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("article-event-dispatch-");
        executor.initialize();
        return executor;
    }

    @Bean
    ThreadPoolTaskExecutor articleProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("article-processing-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    ThreadPoolTaskScheduler articleStreamStartupScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("article-stream-startup-");
        return scheduler;
    }

    @Bean
    @ConditionalOnProperty(
            name = "news.processing.redis.enabled", havingValue = "true", matchIfMissing = true)
    RedisArticleEventStream redisArticleEventStream(
            StringRedisTemplate redis, RedisProcessingProperties properties, Clock clock) {
        return new RedisArticleEventStream(redis, properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean(ArticleEventPublisher.class)
    ArticleEventPublisher deferredArticleEventPublisher() {
        return event -> false;
    }
}
