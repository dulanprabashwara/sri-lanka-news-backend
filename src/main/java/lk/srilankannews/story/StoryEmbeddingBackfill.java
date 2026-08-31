package lk.srilankannews.story;

import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@Order(125)
class StoryEmbeddingBackfill implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryEmbeddingBackfill.class);

    private final ArticleRepository articleRepository;
    private final ArticleEmbeddingService embeddingService;
    private final StoryClusteringService clusteringService;
    private final StoryEmbeddingProperties properties;

    StoryEmbeddingBackfill(
            ArticleRepository articleRepository,
            ArticleEmbeddingService embeddingService,
            StoryClusteringService clusteringService,
            StoryEmbeddingProperties properties) {
        this.articleRepository = articleRepository;
        this.embeddingService = embeddingService;
        this.clusteringService = clusteringService;
        this.properties = properties;
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
        List<Article> articles = articleRepository.findEmbeddingBackfillCandidates(
                properties.model(), properties.dimensions(), properties.inputVersion(), page);
        int completed = 0;
        int failed = 0;
        for (Article article : articles) {
            try {
                Article embedded = embeddingService.ensureEmbedding(article.id());
                if (embedded.storyId() == null) {
                    clusteringService.cluster(embedded.id());
                }
                completed++;
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.warn("story_embedding_backfill_failed articleId={} reason={}",
                        article.id(), exception.getClass().getSimpleName());
            }
        }
        if (!articles.isEmpty()) {
            LOGGER.info("story_embedding_backfill_completed scanned={} completed={} failed={}",
                    articles.size(), completed, failed);
        }
    }
}
