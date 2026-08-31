package lk.srilankannews.story;

import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.processing.ArticleDiscoveredNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@Order(150)
class StoryClusteringBackfill implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryClusteringBackfill.class);

    private final ArticleRepository articleRepository;
    private final StoryClusteringService clusteringService;
    private final StoryClusteringProperties properties;
    private final ArticleDiscoveredNotifier notifier;

    StoryClusteringBackfill(
            ArticleRepository articleRepository,
            StoryClusteringService clusteringService,
            StoryClusteringProperties properties,
            ArticleDiscoveredNotifier notifier) {
        this.articleRepository = articleRepository;
        this.clusteringService = clusteringService;
        this.properties = properties;
        this.notifier = notifier;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.backfillLimit() == 0) {
            return;
        }
        PageRequest page = PageRequest.of(
                0,
                properties.backfillLimit(),
                Sort.by(Sort.Order.asc("publishedAt"), Sort.Order.asc("id")));
        var articles = articleRepository.findEnrichedWithoutStory(page);
        int completed = 0;
        int failed = 0;
        for (var article : articles) {
            try {
                clusteringService.cluster(article.id());
                completed++;
            } catch (RuntimeException exception) {
                failed++;
                notifier.notifyDiscovered(article);
                LOGGER.warn("story_backfill_failed articleId={} reason={}",
                        article.id(), exception.getClass().getSimpleName());
            }
        }
        if (!articles.isEmpty()) {
            LOGGER.info("story_backfill_completed scanned={} assigned={} failed={}",
                    articles.size(), completed, failed);
        }
    }
}
