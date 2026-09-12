package lk.srilankannews.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.ErrorHandler;

class RedisStreamConfigurationTest {

    private final RedisStreamConfiguration configuration = new RedisStreamConfiguration();

    @Test
    void streamErrorHandlerHandlesExceptionsWithoutThrowing() {
        ErrorHandler errorHandler = configuration.streamErrorHandler();
        assertThat(errorHandler).isNotNull();

        assertThatCode(() -> errorHandler.handleError(new RuntimeException("Lettuce connection dropped")))
                .doesNotThrowAnyException();

        assertThatCode(() -> errorHandler.handleError(new IllegalStateException("Subscription timeout", new RuntimeException("Nested failure"))))
                .doesNotThrowAnyException();
    }

    @Test
    void articleStreamListenerContainerCreatedWithOptions() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisProcessingProperties properties = new RedisProcessingProperties(
                true, "article-discovered", "article-discovered-dlq",
                "article-processing", "test-consumer", 3, Duration.ofSeconds(2));
        ErrorHandler errorHandler = configuration.streamErrorHandler();

        var container = configuration.articleStreamListenerContainer(
                connectionFactory, properties, errorHandler);

        assertThat(container).isNotNull();
    }

    @Test
    void redisArticleStreamListenerBeanCreated() {
        ArticleEventConsumer consumer = mock(ArticleEventConsumer.class);
        var listener = configuration.redisArticleStreamListener(consumer);
        assertThat(listener).isNotNull();
    }
}

