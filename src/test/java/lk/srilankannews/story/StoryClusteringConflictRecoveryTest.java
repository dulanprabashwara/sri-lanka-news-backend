package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class StoryClusteringConflictRecoveryTest {

    @Test
    void retryAfterPartitionConflictSeesWinnersStory() {
        ArticleRepository articles = org.mockito.Mockito.mock(ArticleRepository.class);
        StoryRepository stories = org.mockito.Mockito.mock(StoryRepository.class);
        StoryMatcher matcher = org.mockito.Mockito.mock(StoryMatcher.class);
        StoryPersistence persistence = org.mockito.Mockito.mock(StoryPersistence.class);
        ArticleStoryAssignment assignment = org.mockito.Mockito.mock(ArticleStoryAssignment.class);
        StoryClusterPartitionStore partitions =
                org.mockito.Mockito.mock(StoryClusterPartitionStore.class);
        Article article = StoryClusteringServiceTest.article(
                "article-b", null, StoryClusteringServiceTest.NOW);
        Article representative = StoryClusteringServiceTest.article(
                "article-a", "story-1", StoryClusteringServiceTest.NOW);
        Story winningStory = StoryClusteringServiceTest.story(
                "story-1", "article-a", StoryClusteringServiceTest.NOW, 1);
        AtomicInteger partitionAttempts = new AtomicInteger();

        when(articles.findById("article-b")).thenReturn(Optional.of(article));
        when(stories.findCandidates(any(), any(), any())).thenReturn(List.of(winningStory));
        when(articles.findAllById(List.of("article-a")))
                .thenReturn(List.of(representative));
        when(matcher.score(article, representative)).thenReturn(0.90);
        when(assignment.assignIfAbsent("article-b", "story-1", StoryClusteringServiceTest.NOW))
                .thenReturn("story-1");
        doAnswer(invocation -> {
            if (partitionAttempts.incrementAndGet() == 1) {
                throw new MongoException(112, "WriteConflict");
            }
            return null;
        }).when(partitions).advance("hybrid-v1", StoryClusteringServiceTest.NOW);

        TransactionOperations transactions = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction((TransactionStatus) null);
            }
        };
        StoryClusteringService service = new StoryClusteringService(
                articles, stories, matcher, persistence, assignment, partitions,
                new StoryClusteringTransactionExecutor(transactions),
                new StoryClusteringProperties(Duration.ofHours(48), 0.72, 200, 100),
                Clock.fixed(StoryClusteringServiceTest.NOW, ZoneOffset.UTC));

        assertThat(service.cluster("article-b")).isEqualTo("story-1");

        assertThat(partitionAttempts).hasValue(2);
        verify(persistence, never()).createPending(any(), any());
        verify(persistence).addArticleIfAbsent(
                "story-1", article, StoryClusteringServiceTest.NOW);
    }
}
