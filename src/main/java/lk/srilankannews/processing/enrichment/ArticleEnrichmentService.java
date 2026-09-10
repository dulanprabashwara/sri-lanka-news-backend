package lk.srilankannews.processing.enrichment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lk.srilankannews.ai.AiEntity;
import lk.srilankannews.ai.AiInput;
import lk.srilankannews.ai.AiInputPolicy;
import lk.srilankannews.ai.AiOutputValidator;
import lk.srilankannews.ai.AiProvider;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.ai.AiResult;
import lk.srilankannews.ai.GeminiProperties;
import lk.srilankannews.ai.openrouter.OpenRouterAiProvider;
import lk.srilankannews.ai.openrouter.OpenRouterProperties;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleAiEnrichment;
import lk.srilankannews.article.ArticleEntity;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.article.cache.ArticleFeedCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ArticleEnrichmentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleEnrichmentService.class);

    private final ArticleService articleService;
    private final AiProvider provider;
    private final AiInputPolicy inputPolicy;
    private final AiOutputValidator outputValidator;
    private final GeminiProperties geminiProperties;
    private final ArticleFeedCache feedCache;
    private final ArticleEnrichmentJobStore jobs;
    private final EnrichmentRetryProperties retryProperties;
    private final GeminiRequestController requestController;
    private final Clock clock;
    private final OpenRouterAiProvider openRouterProvider;
    private final OpenRouterProperties openRouterProperties;

    @Autowired
    public ArticleEnrichmentService(
            ArticleService articleService,
            @org.springframework.beans.factory.annotation.Qualifier("aiProvider") AiProvider provider,
            AiInputPolicy inputPolicy,
            AiOutputValidator outputValidator,
            GeminiProperties geminiProperties,
            ArticleFeedCache feedCache,
            ArticleEnrichmentJobStore jobs,
            EnrichmentRetryProperties retryProperties,
            GeminiRequestController requestController,
            Clock clock,
            @Autowired(required = false) OpenRouterAiProvider openRouterProvider,
            @Autowired(required = false) OpenRouterProperties openRouterProperties) {
        this.articleService = articleService;
        this.provider = provider;
        this.inputPolicy = inputPolicy;
        this.outputValidator = outputValidator;
        this.geminiProperties = geminiProperties;
        this.feedCache = feedCache;
        this.jobs = jobs;
        this.retryProperties = retryProperties;
        this.requestController = requestController;
        this.clock = clock;
        this.openRouterProvider = openRouterProvider;
        this.openRouterProperties = openRouterProperties;
    }

    public ArticleEnrichmentService(
            ArticleService articleService,
            AiProvider provider,
            AiInputPolicy inputPolicy,
            AiOutputValidator outputValidator,
            GeminiProperties geminiProperties,
            ArticleFeedCache feedCache,
            ArticleEnrichmentJobStore jobs,
            EnrichmentRetryProperties retryProperties,
            GeminiRequestController requestController,
            Clock clock) {
        this(articleService, provider, inputPolicy, outputValidator,
                geminiProperties, feedCache, jobs, retryProperties, requestController, clock,
                null, null);
    }

    public Outcome attempt(String articleId) {
        Article article = articleService.findById(articleId)
                .orElseThrow(() -> new IllegalStateException("Article does not exist"));
        Instant now = clock.instant();
        jobs.ensurePending(articleId, now);
        if (currentEnrichment(article)) {
            jobs.markAlreadySucceeded(articleId, now);
            return Outcome.ALREADY_SUCCEEDED;
        }

        return jobs.claim(articleId, now, retryProperties.leaseDuration(),
                        retryProperties.maxAttempts())
                .map(job -> enrich(article, job))
                .orElse(Outcome.NOT_DUE_OR_CLAIMED);
    }

    private Outcome enrich(Article article, ArticleEnrichmentJob job) {
        String token = job.claimToken();
        var permit = requestController.tryAcquireBackground();
        if (permit.isEmpty()) {
            if (openRouterAvailable() && requestController.isCoolingDown()) {
                return tryOpenRouterFallback(article, job, token, null);
            }
            return defer(job, token, "BACKGROUND_CAPACITY", null);
        }
        try (GeminiRequestController.Permit ignored = permit.get()) {
            articleService.updateProcessingStatus(article.id(), ProcessingStatus.PROCESSING)
                    .orElseThrow(() -> new IllegalStateException(
                            "Article disappeared during processing"));
            String content = inputPolicy.prepare(article.extractedContent());
            AiResult result = outputValidator.validate(provider.enrich(
                    new AiInput(article.title(), content, article.originalLanguage())));
            ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                    result.summary(), result.topics(), result.keywords(),
                    result.entities().stream().map(this::toArticleEntity).toList(),
                    geminiProperties.model(), geminiProperties.promptVersion(), clock.instant());
            articleService.completeEnrichment(article.id(), enrichment, result.category())
                    .orElseThrow(() -> new IllegalStateException(
                            "Article disappeared during enrichment"));
            jobs.markSucceeded(article.id(), token, clock.instant());
            invalidateFeedCache(article.id());
            LOGGER.info("article_enrichment_succeeded provider=GEMINI model={} articleId={}",
                    geminiProperties.model(), article.id());
            return Outcome.SUCCEEDED;
        } catch (AiProviderException exception) {
            if (retryable(exception)) {
                if (exception.kind() == AiProviderException.Kind.RATE_LIMIT) {
                    requestController.recordRateLimit(exception.retryAfter());
                }
                LOGGER.warn(
                        "article_enrichment_primary_failed articleId={} attempt={} provider={} category={} "
                                + "httpStatus={} providerCode={} model={}",
                        article.id(), job.attempts(), exception.provider(), exception.kind(),
                        exception.httpStatus(), exception.providerCode(), exception.model());

                if (openRouterAvailable()) {
                    return tryOpenRouterFallback(article, job, token, exception);
                }

                Outcome outcome = defer(job, token, exception.kind().name(), exception.retryAfter());
                LOGGER.warn(
                        "article_enrichment_deferred articleId={} attempt={} category={} "
                                + "httpStatus={} providerCode={} model={}",
                        article.id(), job.attempts(), exception.kind(), exception.httpStatus(),
                        exception.providerCode(), exception.model());
                return outcome;
            }
            jobs.markFailed(article.id(), token, exception.kind().name(), clock.instant());
            LOGGER.warn(
                    "article_enrichment_failed articleId={} attempt={} category={} "
                            + "httpStatus={} providerCode={} model={}",
                    article.id(), job.attempts(), exception.kind(), exception.httpStatus(),
                    exception.providerCode(), exception.model());
            return Outcome.FAILED;
        }
    }

    private Outcome tryOpenRouterFallback(
            Article article, ArticleEnrichmentJob job, String token, AiProviderException primaryException) {
        try {
            articleService.updateProcessingStatus(article.id(), ProcessingStatus.PROCESSING)
                    .orElseThrow(() -> new IllegalStateException(
                            "Article disappeared during processing"));
            String content = inputPolicy.prepare(article.extractedContent());
            AiResult result = outputValidator.validate(openRouterProvider.enrich(
                    new AiInput(article.title(), content, article.originalLanguage())));
            ArticleAiEnrichment enrichment = new ArticleAiEnrichment(
                    result.summary(), result.topics(), result.keywords(),
                    result.entities().stream().map(this::toArticleEntity).toList(),
                    openRouterProperties.model(), geminiProperties.promptVersion(), clock.instant());
            articleService.completeEnrichment(article.id(), enrichment, result.category())
                    .orElseThrow(() -> new IllegalStateException(
                            "Article disappeared during enrichment"));
            jobs.markSucceeded(article.id(), token, clock.instant());
            invalidateFeedCache(article.id());
            LOGGER.info("article_enrichment_succeeded provider=OPENROUTER model={} articleId={}",
                    openRouterProperties.model(), article.id());
            return Outcome.SUCCEEDED;
        } catch (AiProviderException openRouterException) {
            String deferReason = primaryException != null
                    ? primaryException.kind().name()
                    : openRouterException.kind().name();
            Duration retryAfter = primaryException != null && primaryException.retryAfter() != null
                    ? primaryException.retryAfter()
                    : openRouterException.retryAfter();
            Outcome outcome = defer(job, token, deferReason, retryAfter);
            LOGGER.warn(
                    "article_enrichment_fallback_failed articleId={} attempt={} provider={} category={} "
                            + "httpStatus={} providerCode={} model={}",
                    article.id(), job.attempts(), openRouterException.provider(),
                    openRouterException.kind(), openRouterException.httpStatus(),
                    openRouterException.providerCode(), openRouterException.model());
            return outcome;
        } catch (RuntimeException exception) {
            String deferReason = primaryException != null
                    ? primaryException.kind().name()
                    : "FALLBACK_FAILURE";
            Duration retryAfter = primaryException != null ? primaryException.retryAfter() : null;
            Outcome outcome = defer(job, token, deferReason, retryAfter);
            LOGGER.warn("article_enrichment_fallback_error articleId={} attempt={} reason={}",
                    article.id(), job.attempts(), exception.getMessage());
            return outcome;
        }
    }

    private Outcome defer(ArticleEnrichmentJob job, String token, String reason, Duration retryAfter) {
        Instant now = clock.instant();
        if (job.attempts() >= retryProperties.maxAttempts()) {
            jobs.markFailed(job.articleId(), token, "RETRIES_EXHAUSTED_" + reason, now);
            return Outcome.FAILED;
        }
        Duration delay = (retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero())
                ? retryAfter
                : backoff(job.articleId(), job.attempts());
        jobs.markDeferred(job.articleId(), token, reason, now.plus(delay), now);
        return Outcome.DEFERRED;
    }

    private Duration backoff(String articleId, int attempts) {
        long exponent = Math.max(0, Math.min(attempts - 1, 30));
        long baseMillis;
        try {
            baseMillis = Math.multiplyExact(
                    retryProperties.initialBackoff().toMillis(), 1L << exponent);
        } catch (ArithmeticException exception) {
            baseMillis = retryProperties.maxBackoff().toMillis();
        }
        baseMillis = Math.min(baseMillis, retryProperties.maxBackoff().toMillis());
        int jitterPercent = Math.floorMod(articleId.hashCode() + attempts * 31, 21);
        long jitter = Math.round(baseMillis * (jitterPercent / 100.0));
        return Duration.ofMillis(Math.min(
                retryProperties.maxBackoff().toMillis(), baseMillis + jitter));
    }

    private boolean retryable(AiProviderException exception) {
        return exception.kind() == AiProviderException.Kind.RATE_LIMIT
                || exception.kind() == AiProviderException.Kind.TIMEOUT_NETWORK;
    }

    private boolean openRouterAvailable() {
        return openRouterProvider != null && openRouterProperties != null && openRouterProperties.configured();
    }

    private boolean currentEnrichment(Article article) {
        if (article.aiEnrichment() == null) {
            return false;
        }
        if (article.aiEnrichment().matches(geminiProperties.model(), geminiProperties.promptVersion())) {
            return true;
        }
        if (openRouterProperties != null && openRouterProperties.configured()
                && article.aiEnrichment().matches(openRouterProperties.model(), geminiProperties.promptVersion())) {
            return true;
        }
        return false;
    }

    private ArticleEntity toArticleEntity(AiEntity entity) {
        return new ArticleEntity(entity.name(), entity.type());
    }

    private void invalidateFeedCache(String articleId) {
        try {
            feedCache.invalidate();
        } catch (RuntimeException exception) {
            LOGGER.warn("article_feed_cache_invalidation_failed articleId={} reason={}",
                    articleId, exception.getClass().getSimpleName());
        }
    }

    public enum Outcome {
        SUCCEEDED,
        ALREADY_SUCCEEDED,
        DEFERRED,
        FAILED,
        NOT_DUE_OR_CLAIMED;

        public boolean enrichmentAvailable() {
            return this == SUCCEEDED || this == ALREADY_SUCCEEDED;
        }
    }
}
