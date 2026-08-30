package lk.srilankannews.processing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

@Configuration
@ConditionalOnProperty(
        name = "news.processing.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamConfiguration {

    @Bean
    StreamMessageListenerContainer<String, MapRecord<String, String, String>>
            articleStreamListenerContainer(
                    RedisConnectionFactory connectionFactory,
                    RedisProcessingProperties properties) {
        var options = StreamMessageListenerContainer
                .StreamMessageListenerContainerOptions
                .<String, MapRecord<String, String, String>>builder()
                .pollTimeout(properties.pollTimeout())
                .build();
        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    @Bean
    RedisArticleStreamListener redisArticleStreamListener(ArticleEventConsumer consumer) {
        return new RedisArticleStreamListener(consumer);
    }
}
