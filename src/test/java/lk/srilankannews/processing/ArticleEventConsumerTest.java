package lk.srilankannews.processing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class ArticleEventConsumerTest {
    @Mock
    private ArticleProcessingWorker worker;
    @Mock
    private ArticleEventStream stream;
    private ArticleEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ArticleEventConsumer(worker, stream, properties());
    }

    @Test
    void acknowledgesSuccessfullyProcessedEvent() {
        consumer.consume("1-0", event(1));

        verify(worker).process(event(1));
        verify(stream).acknowledge("1-0");
        verify(stream, never()).publish(any());
    }

    @Test
    void republishesBoundedRetryBeforeAcknowledgingOriginal() {
        org.mockito.Mockito.doThrow(new IllegalStateException("failed"))
                .when(worker).process(event(1));
        when(stream.publish(event(2))).thenReturn(true);

        consumer.consume("1-0", event(1));

        verify(worker).markRetrying("article-1");
        verify(stream).publish(event(2));
        verify(stream).acknowledge("1-0");
    }

    @Test
    void deadLettersAndMarksFailedAfterMaximumAttempts() {
        org.mockito.Mockito.doThrow(new IllegalStateException("failed"))
                .when(worker).process(event(3));
        when(stream.publishDeadLetter(event(3), "IllegalStateException")).thenReturn(true);

        consumer.consume("3-0", event(3));

        verify(worker).markFailed("article-1");
        verify(stream).publishDeadLetter(event(3), "IllegalStateException");
        verify(stream).acknowledge("3-0");
    }

    @Test
    void leavesOriginalPendingWhenRetryCannotBePublished() {
        org.mockito.Mockito.doThrow(new IllegalStateException("failed"))
                .when(worker).process(event(1));
        when(stream.publish(event(2))).thenReturn(false);

        consumer.consume("1-0", event(1));

        verify(stream, never()).acknowledge("1-0");
    }

    private ArticleDiscoveredEvent event(int attempt) {
        return new ArticleDiscoveredEvent(
                "event-1", "article-1", "source-1",
                Instant.parse("2026-08-30T10:00:00Z"), 1, attempt);
    }

    private RedisProcessingProperties properties() {
        return new RedisProcessingProperties(
                true, "article-discovered", "article-discovered-dlq",
                "article-processing", "test-consumer", 3, Duration.ofSeconds(2));
    }
}
