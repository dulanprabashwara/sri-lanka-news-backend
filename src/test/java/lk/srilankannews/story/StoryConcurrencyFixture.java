package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lk.srilankannews.article.*;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.*;

class StoryConcurrencyFixture {
    static final Instant NOW = StoryClusteringServiceTest.NOW;
    final ArticleRepository articles = org.mockito.Mockito.mock(ArticleRepository.class);
    final StoryRepository stories = org.mockito.Mockito.mock(StoryRepository.class);
    final StoryMatcher matcher = org.mockito.Mockito.mock(StoryMatcher.class);
    final StoryPersistence persistence = org.mockito.Mockito.mock(StoryPersistence.class);
    final ArticleStoryAssignment assignment =
            org.mockito.Mockito.mock(ArticleStoryAssignment.class);
    final StoryClusterPartitionStore partitions =
            org.mockito.Mockito.mock(StoryClusterPartitionStore.class);
    final Map<String, Article> storedArticles = new ConcurrentHashMap<>();
    final AtomicReference<Story> storedStory = new AtomicReference<>();
    final Set<String> members = ConcurrentHashMap.newKeySet();
    final AtomicInteger storyCreates = new AtomicInteger();
    final StoryClusteringService service;

    StoryConcurrencyFixture() {
        storedArticles.put("article-a", article("article-a", null));
        storedArticles.put("article-b", article("article-b", null));
        CountDownLatch bothPreflightReads = new CountDownLatch(2);
        Map<String, AtomicInteger> loads = new ConcurrentHashMap<>();

        when(articles.findById(any())).thenAnswer(call -> {
            String id = call.getArgument(0);
            if (loads.computeIfAbsent(id, ignored -> new AtomicInteger())
                    .incrementAndGet() == 1) {
                bothPreflightReads.countDown();
                assertThat(bothPreflightReads.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return Optional.ofNullable(storedArticles.get(id));
        });
        when(stories.findCandidates(any(), any(), any())).thenAnswer(call -> {
            Story story = storedStory.get();
            return story == null || story.articleCount() == 0
                    ? List.of() : List.of(story);
        });
        when(articles.findAllById(any())).thenAnswer(call -> {
            Iterable<String> ids = call.getArgument(0);
            List<Article> found = new ArrayList<>();
            ids.forEach(id -> found.add(storedArticles.get(id)));
            return found;
        });
        when(matcher.score(any(), any())).thenReturn(0.90);
        when(persistence.createPending(any(), any())).thenAnswer(call -> {
            Article representative = call.getArgument(0);
            Story story = StoryClusteringServiceTest.story(
                    "story-1", representative.id(), representative.publishedAt(), 0);
            storedStory.set(story);
            storyCreates.incrementAndGet();
            return new StoryPersistence.Creation(story, true);
        });
        when(assignment.assignIfAbsent(any(), any(), any())).thenAnswer(call -> {
            String articleId = call.getArgument(0);
            String storyId = call.getArgument(1);
            storedArticles.compute(articleId, (ignored, current) ->
                    current.storyId() == null ? withStory(current, storyId) : current);
            return storedArticles.get(articleId).storyId();
        });
        when(persistence.addArticleIfAbsent(any(), any(), any())).thenAnswer(call -> {
            String storyId = call.getArgument(0);
            Article article = call.getArgument(1);
            if (!storyId.equals(storedArticles.get(article.id()).storyId())
                    || !members.add(article.id())) {
                return false;
            }
            Story current = storedStory.get();
            storedStory.set(new Story(
                    current.id(), current.canonicalTitle(), current.representativeArticleId(),
                    current.category(), current.firstPublishedAt(), current.lastPublishedAt(),
                    members.size(), Set.of("source-article-a", "source-article-b"),
                    Set.copyOf(members), current.createdAt(), NOW, current.matchingVersion()));
            return true;
        });

        TransactionOperations serialized = new TransactionOperations() {
            @Override
            public synchronized <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction((TransactionStatus) null);
            }
        };
        service = new StoryClusteringService(
                articles, stories, matcher, persistence, assignment, partitions,
                new StoryClusteringTransactionExecutor(serialized),
                new StoryClusteringProperties(Duration.ofHours(48), 0.72, 200, 100),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Article article(String id, String storyId) {
        return StoryClusteringServiceTest.article(id, storyId, NOW);
    }

    private Article withStory(Article article, String storyId) {
        return new Article(
                article.id(), article.sourceId(), article.title(), article.originalUrl(),
                article.canonicalUrl(), article.originalLanguage(), article.authors(),
                article.publishedAt(), article.discoveredAt(), article.category(),
                article.extractedContent(), article.contentHash(), article.aiEnrichment(),
                article.processingStatus(), storyId, article.createdAt(), NOW);
    }
}
