package lk.srilankannews.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ArticleEventConsumerConcurrencyTest {

    @Test
    void slowEnrichmentDoesNotBlockTranslationOfLaterArticle() throws Exception {
        ArticleProcessingWorker worker = mock(ArticleProcessingWorker.class);
        ArticleEventStream stream = mock(ArticleEventStream.class);
        ExecutorService downstreamExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch firstEnrichmentStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstEnrichment = new CountDownLatch(1);
        CountDownLatch secondTranslationReached = new CountDownLatch(1);
        ArticleDiscoveredEvent first = event("event-1", "article-1");
        ArticleDiscoveredEvent second = event("event-2", "article-2");
        doAnswer(invocation -> {
            firstEnrichmentStarted.countDown();
            releaseFirstEnrichment.await(5, TimeUnit.SECONDS);
            return null;
        }).when(worker).processAfterTranslation(first);
        doAnswer(invocation -> {
            secondTranslationReached.countDown();
            return null;
        }).when(worker).translate(second);

        ArticleEventConsumer consumer = new ArticleEventConsumer(
                worker, stream, properties(), downstreamExecutor);
        try {
            consumer.consume("1-0", first);
            assertThat(firstEnrichmentStarted.await(1, TimeUnit.SECONDS)).isTrue();

            consumer.consume("2-0", second);

            assertThat(secondTranslationReached.await(1, TimeUnit.SECONDS)).isTrue();
            verify(worker).translate(first);
            verify(worker).translate(second);
        } finally {
            releaseFirstEnrichment.countDown();
            downstreamExecutor.shutdownNow();
        }
    }

    private ArticleDiscoveredEvent event(String eventId, String articleId) {
        return new ArticleDiscoveredEvent(
                eventId, articleId, "source-1", Instant.parse("2026-09-10T00:00:00Z"),
                ArticleDiscoveredEvent.CURRENT_VERSION, 1);
    }

    private RedisProcessingProperties properties() {
        return new RedisProcessingProperties(
                true, "article-discovered", "article-discovered-dlq",
                "article-processing", "test-consumer", 3, Duration.ofSeconds(2));
    }
}
