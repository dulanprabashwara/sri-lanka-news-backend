package lk.srilankannews.processing.enrichment;

import java.time.Clock;
import lk.srilankannews.story.ArticleEmbeddingService;
import lk.srilankannews.story.StoryClusteringService;
import lk.srilankannews.translation.ArticleTranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "news.ai.enrichment.retry.enabled", havingValue = "true", matchIfMissing = true)
public class DeferredEnrichmentScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeferredEnrichmentScheduler.class);

    private final ArticleEnrichmentJobStore jobs;
    private final ArticleEnrichmentService enrichmentService;
    private final ArticleTranslationService translationService;
    private final ArticleEmbeddingService embeddingService;
    private final StoryClusteringService clusteringService;
    private final EnrichmentRetryProperties properties;
    private final Clock clock;

    public DeferredEnrichmentScheduler(
            ArticleEnrichmentJobStore jobs,
            ArticleEnrichmentService enrichmentService,
            ArticleTranslationService translationService,
            ArticleEmbeddingService embeddingService,
            StoryClusteringService clusteringService,
            EnrichmentRetryProperties properties,
            Clock clock) {
        this.jobs = jobs;
        this.enrichmentService = enrichmentService;
        this.translationService = translationService;
        this.embeddingService = embeddingService;
        this.clusteringService = clusteringService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${news.ai.enrichment.retry.poll-interval:60s}")
    public void retryDue() {
        for (String articleId : jobs.findDueArticleIds(
                clock.instant(), properties.maxAttempts(), properties.batchSize())) {
            try {
                ArticleEnrichmentService.Outcome outcome = enrichmentService.attempt(articleId);
                if (outcome == ArticleEnrichmentService.Outcome.SUCCEEDED) {
                    translationService.ensureTranslations(articleId);
                    embeddingService.ensureEmbedding(articleId);
                    clusteringService.cluster(articleId);
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("deferred_enrichment_followup_failed articleId={} reason={}",
                        articleId, exception.getClass().getSimpleName());
            }
        }
    }
}
