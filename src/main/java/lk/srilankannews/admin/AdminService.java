package lk.srilankannews.admin;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.processing.ArticleDiscoveredNotifier;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private static final int RECENT_FAILURE_LIMIT = 10;
    private final AdminMongoOperations operations;
    private final SourceRepository sourceRepository;
    private final ArticleRepository articleRepository;
    private final ArticleDiscoveredNotifier notifier;
    private final Clock clock;

    public AdminService(
            AdminMongoOperations operations,
            SourceRepository sourceRepository,
            ArticleRepository articleRepository,
            ArticleDiscoveredNotifier notifier,
            Clock clock) {
        this.operations = operations;
        this.sourceRepository = sourceRepository;
        this.articleRepository = articleRepository;
        this.notifier = notifier;
        this.clock = clock;
    }

    public AdminOverviewResponse overview() {
        var articleCounts = new AdminOverviewResponse.ArticleCounts(
                operations.articleCount(),
                operations.articleCount(ProcessingStatus.PENDING),
                operations.articleCount(ProcessingStatus.PROCESSING),
                operations.articleCount(ProcessingStatus.COMPLETED),
                operations.articleCount(ProcessingStatus.RETRYING),
                operations.articleCount(ProcessingStatus.FAILED));
        List<AdminArticleResponse> failures = articleResponses(
                operations.findArticles(ProcessingStatus.FAILED, null, RECENT_FAILURE_LIMIT));
        return new AdminOverviewResponse(
                new AdminOverviewResponse.SourceCounts(operations.sourceCount()),
                articleCounts,
                new AdminOverviewResponse.StoryCounts(operations.storyCount()),
                failures);
    }

    public List<AdminSourceResponse> sources() {
        Map<String, Long> counts = operations.articleCountsBySource();
        return sourceRepository.findAllByOrderByNameAsc().stream()
                .map(source -> new AdminSourceResponse(
                        source.id(), source.name(), source.slug(), source.baseUrl(),
                        source.defaultLanguage(), source.ingestionType(), source.enabled(),
                        counts.getOrDefault(source.id(), 0L), source.createdAt(), source.updatedAt()))
                .toList();
    }

    public List<AdminArticleResponse> articles(
            ProcessingStatus status, String sourceSlug, int limit) {
        String sourceId = sourceSlug == null ? null : sourceRepository.findBySlug(sourceSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Source")).id();
        return articleResponses(operations.findArticles(status, sourceId, limit));
    }

    public AdminArticleResponse retry(String articleId) {
        Article claimed = operations.claimFailedForRetry(articleId, clock.instant());
        if (claimed == null) {
            if (!articleRepository.existsById(articleId)) {
                throw new ResourceNotFoundException("Article");
            }
            throw new AdminRetryNotAllowedException();
        }
        notifier.notifyDiscovered(claimed);
        return articleResponses(List.of(claimed)).get(0);
    }

    private List<AdminArticleResponse> articleResponses(List<Article> articles) {
        Set<String> sourceIds = articles.stream().map(Article::sourceId).collect(Collectors.toSet());
        Map<String, Source> sources = sourceIds.isEmpty() ? Map.of()
                : stream(sourceRepository.findAllById(sourceIds))
                        .collect(Collectors.toUnmodifiableMap(Source::id, Function.identity()));
        return articles.stream().map(article -> {
            Source source = sources.get(article.sourceId());
            if (source == null) throw new IllegalStateException("Article source is missing.");
            return new AdminArticleResponse(
                    article.id(), article.title(), new AdminSourceSummary(source.name(), source.slug()),
                    article.processingStatus(), article.discoveredAt(), article.publishedAt());
        }).toList();
    }

    private <T> java.util.stream.Stream<T> stream(Iterable<T> values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false);
    }
}
