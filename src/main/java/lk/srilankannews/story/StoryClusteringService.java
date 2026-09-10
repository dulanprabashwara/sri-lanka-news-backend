package lk.srilankannews.story;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StoryClusteringService {
    private final ArticleRepository articleRepository;
    private final StoryRepository storyRepository;
    private final StoryMatcher lexicalMatcher;
    private final StorySemanticMatcher semanticMatcher;
    private final StoryPersistence persistence;
    private final ArticleStoryAssignment assignment;
    private final StoryClusterPartitionStore partitionStore;
    private final StoryClusteringTransactionExecutor transactionExecutor;
    private final StoryClusteringProperties properties;
    private final Clock clock;

    StoryClusteringService(
            ArticleRepository articleRepository,
            StoryRepository storyRepository,
            StoryMatcher lexicalMatcher,
            StoryPersistence persistence,
            ArticleStoryAssignment assignment,
            StoryClusterPartitionStore partitionStore,
            StoryClusteringTransactionExecutor transactionExecutor,
            StoryClusteringProperties properties,
            Clock clock) {
        this(
                articleRepository, storyRepository, lexicalMatcher,
                new StorySemanticMatcher(new StoryEmbeddingProperties(
                        "test-embedding", 3, "story-semantic-v2", 8000,
                        0.82, 0.90, 0)),
                persistence, assignment, partitionStore, transactionExecutor,
                properties, clock);
    }

    @Autowired
    public StoryClusteringService(
            ArticleRepository articleRepository,
            StoryRepository storyRepository,
            StoryMatcher lexicalMatcher,
            StorySemanticMatcher semanticMatcher,
            StoryPersistence persistence,
            ArticleStoryAssignment assignment,
            StoryClusterPartitionStore partitionStore,
            StoryClusteringTransactionExecutor transactionExecutor,
            StoryClusteringProperties properties,
            Clock clock) {
        this.articleRepository = articleRepository;
        this.storyRepository = storyRepository;
        this.lexicalMatcher = lexicalMatcher;
        this.semanticMatcher = semanticMatcher;
        this.persistence = persistence;
        this.assignment = assignment;
        this.partitionStore = partitionStore;
        this.transactionExecutor = transactionExecutor;
        this.properties = properties;
        this.clock = clock;
    }

    public String cluster(String articleId) {
        return transactionExecutor.execute(
                () -> clusterInTransaction(articleId, StoryClusterPartition.activeId()));
    }

    private String clusterInTransaction(String articleId, String partitionId) {
        Instant now = clock.instant();
        partitionStore.advance(partitionId, now);

        Article article = loadEnrichedArticle(articleId);
        if (article.storyId() != null) {
            persistence.addArticleIfAbsent(article.storyId(), article, now);
            return article.storyId();
        }

        Story target = selectCandidate(article);
        StoryPersistence.Creation creation = null;
        if (target == null) {
            creation = persistence.createPending(article, now);
            target = creation.story();
        }

        String assignedStoryId = assignment.assignIfAbsent(article.id(), target.id(), now);
        if (assignedStoryId.equals(target.id())) {
            persistence.addArticleIfAbsent(target.id(), article, now);
            return target.id();
        }

        if (creation != null) {
            persistence.discardIfUnused(creation);
        }
        persistence.addArticleIfAbsent(assignedStoryId, article, now);
        return assignedStoryId;
    }

    private Article loadEnrichedArticle(String articleId) {
        return articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalStateException(
                        "Article does not exist for clustering"));
    }

    private Story selectCandidate(Article article) {
        Instant earliest = article.publishedAt().minus(properties.window());
        Instant latest = article.publishedAt().plus(properties.window());
        PageRequest page = PageRequest.of(
                0,
                properties.candidateLimit(),
                Sort.by(Sort.Order.desc("lastPublishedAt"), Sort.Order.asc("id")));
        var stories = storyRepository.findCandidates(earliest, latest, page);
        if (stories.isEmpty()) {
            return null;
        }

        Map<String, Article> representatives = articleRepository.findAllById(
                        stories.stream().map(Story::representativeArticleId).toList())
                .stream()
                .collect(Collectors.toMap(Article::id, Function.identity()));

        return stories.stream()
                .map(story -> match(article, story, representatives.get(
                        story.representativeArticleId())))
                .filter(match -> match.score() >= properties.threshold())
                .min(Comparator
                        .comparingDouble(Match::score).reversed()
                        .thenComparing(
                                match -> match.story().lastPublishedAt(),
                                Comparator.reverseOrder())
                        .thenComparing(match -> match.story().id()))
                .map(Match::story)
                .orElse(null);
    }

    private Match match(Article article, Story story, Article representative) {
        if (representative == null) {
            return new Match(story, 0);
        }
        double lexical = lexicalMatcher.score(article, representative);
        return new Match(story, semanticMatcher.score(article, representative, lexical));
    }

    private record Match(Story story, double score) {
    }
}
