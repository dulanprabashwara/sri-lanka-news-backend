package lk.srilankannews.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ArticleDiscoveredNotifierTest {
    private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");

    @Test
    void publishesOneMinimalVersionedEvent() {
        ArticleEventPublisher publisher = org.mockito.Mockito.mock(ArticleEventPublisher.class);
        when(publisher.publish(any())).thenReturn(true);
        ArticleDiscoveredNotifier notifier = new ArticleDiscoveredNotifier(
                publisher, Clock.fixed(NOW, ZoneOffset.UTC), Runnable::run);

        notifier.notifyDiscovered(article());

        ArgumentCaptor<ArticleDiscoveredEvent> captor =
                ArgumentCaptor.forClass(ArticleDiscoveredEvent.class);
        verify(publisher).publish(captor.capture());
        ArticleDiscoveredEvent event = captor.getValue();
        assertThat(event.articleId()).isEqualTo("article-1");
        assertThat(event.sourceId()).isEqualTo("source-1");
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.attempt()).isEqualTo(1);
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.toFields()).containsOnlyKeys(
                "eventType", "eventId", "articleId", "sourceId",
                "occurredAt", "eventVersion", "attempt");
        assertThat(event.toFields()).doesNotContainKeys("extractedContent", "contentHash");
    }

    @Test
    void doesNotPropagatePublisherFailure() {
        ArticleEventPublisher publisher = org.mockito.Mockito.mock(ArticleEventPublisher.class);
        when(publisher.publish(any())).thenThrow(new IllegalStateException("Redis unavailable"));
        ArticleDiscoveredNotifier notifier = new ArticleDiscoveredNotifier(
                publisher, Clock.fixed(NOW, ZoneOffset.UTC), Runnable::run);

        assertThatCode(() -> notifier.notifyDiscovered(article())).doesNotThrowAnyException();
    }

    private Article article() {
        return new Article(
                "article-1", "source-1", "Headline", "https://example.com/1",
                "https://example.com/1", Language.EN, List.of(), NOW, NOW,
                ArticleCategory.LOCAL, "Internal content", NOW, NOW);
    }
}
