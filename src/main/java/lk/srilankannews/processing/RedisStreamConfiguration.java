package lk.srilankannews.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.util.ErrorHandler;

@Configuration
@ConditionalOnProperty(
        name = "news.processing.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisStreamConfiguration.class);

    @Bean
    StreamMessageListenerContainer<String, MapRecord<String, String, String>>
            articleStreamListenerContainer(
                    RedisConnectionFactory connectionFactory,
                    RedisProcessingProperties properties) {
                    RedisProcessingProperties properties,
                    ErrorHandler streamErrorHandler) {
        var options = StreamMessageListenerContainer
                .StreamMessageListenerContainerOptions
                .<String, MapRecord<String, String, String>>builder()
                .pollTimeout(properties.pollTimeout())
                .errorHandler(streamErrorHandler)
                .build();
        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    @Bean
    ErrorHandler streamErrorHandler() {
        return throwable -> {
            RedisFailureDescription.Details details = RedisFailureDescription.from(throwable);
            LOGGER.error("article_stream_listener_error reason={} rootCause={} message={}",
                    throwable.getClass().getSimpleName(),
                    details.rootCause(),
                    details.message());
        };
    }

    @Bean
    RedisArticleStreamListener redisArticleStreamListener(ArticleEventConsumer consumer) {
        return new RedisArticleStreamListener(consumer);
    }
}
