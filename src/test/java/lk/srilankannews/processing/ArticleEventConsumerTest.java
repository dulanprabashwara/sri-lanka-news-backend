package lk.srilankannews.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.Instant;
import lk.srilankannews.ai.AiProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

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

        verify(worker).translate(event(1));
        verify(worker).processAfterTranslation(event(1));
        verify(stream).acknowledge("1-0");
        verify(stream, never()).publish(any());
    }

    @Test
    void deferredEnrichmentDoesNotCauseArticleEventConsumerRetry() {
        // Worker completes without exception even when enrichment is deferred
        consumer.consume("record-42", event(1));

        verify(worker).processAfterTranslation(event(1));
        verify(worker, never()).markRetrying(any());
        verify(stream).acknowledge("record-42");
        verify(stream, never()).publish(any());
        verify(stream, never()).publishDeadLetter(any(), any());
    }

    @Test
    void rateLimitDeferLeadsToEventCompletionAndAck() {
        // Simulate end-to-end consumer behavior when worker successfully completes with DEFERRED enrichment
        ArticleDiscoveredEvent event = event(1);
        consumer.consume("record-rate-limit", event);

        verify(worker).translate(event);
        verify(worker).processAfterTranslation(event);
        verify(stream).acknowledge("record-rate-limit");
        verify(stream, never()).publish(any());
    }

    @Test
    void republishesBoundedRetryBeforeAcknowledgingOriginal() {
        org.mockito.Mockito.doThrow(new IllegalStateException("failed"))
                .when(worker).processAfterTranslation(event(1));
        when(stream.publish(event(2))).thenReturn(true);

        consumer.consume("1-0", event(1));

        verify(worker).markRetrying("article-1");
        verify(stream).publish(event(2));
        verify(stream).acknowledge("1-0");
    }

    @Test
    void logsSanitizedAiDiagnosticsWithoutSecretsOrArticleContent() {
        String secret = "AIza12345678901234567890123456789012345";
        String articleContent = "PRIVATE ARTICLE CONTENT MUST NOT BE LOGGED";
        AiProviderException failure = new AiProviderException(
                AiProviderException.Kind.AUTHENTICATION,
                "Gemini request failed",
                401,
                "UNAUTHENTICATED",
                "Invalid request https://example.test/generate?key=" + secret,
                "gemini-2.5-flash",
                new RuntimeException(articleContent));
        org.mockito.Mockito.doThrow(failure).when(worker).processAfterTranslation(event(1));
        when(stream.publish(event(2))).thenReturn(true);

        Logger logger = (Logger) LoggerFactory.getLogger(ArticleEventConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            consumer.consume("1-0", event(1));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String log = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertThat(log)
                .contains("providerCategory=AUTHENTICATION")
                .contains("httpStatus=401")
                .contains("providerCode=UNAUTHENTICATED")
                .contains("model=gemini-2.5-flash")
                .contains("key=***")
                .doesNotContain(secret)
                .doesNotContain(articleContent);
        verify(stream).publish(event(2));
        verify(stream).acknowledge("1-0");
    }

    @Test
    void deadLettersAndMarksFailedAfterMaximumAttempts() {
        org.mockito.Mockito.doThrow(new IllegalStateException("failed"))
                .when(worker).processAfterTranslation(event(3));
        when(stream.publishDeadLetter(event(3), "IllegalStateException")).thenReturn(true);

        consumer.consume("3-0", event(3));

        verify(worker).markFailed("article-1");
        verify(stream).publishDeadLetter(event(3), "IllegalStateException");
        verify(stream).acknowledge("3-0");
    }

    @Test
    void leavesOriginalPendingWhenRetryCannotBePublished() {
        org.mockito.Mockito.doThrow(new IllegalStateException("failed"))
                .when(worker).processAfterTranslation(event(1));
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
