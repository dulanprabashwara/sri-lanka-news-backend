package lk.srilankannews.translation;

import java.time.Clock;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.ArticleTranslation;
import lk.srilankannews.article.cache.ArticleFeedCache;
import lk.srilankannews.common.domain.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ArticleTranslationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleTranslationService.class);

    private final ArticleService articleService;
    private final TranslationProvider provider;
    private final TranslationInputFactory inputFactory;
    private final TranslationOutputValidator outputValidator;
    private final TranslationProperties properties;
    private final ArticleFeedCache feedCache;
    private final Clock clock;

    public ArticleTranslationService(
            ArticleService articleService,
            TranslationProvider provider,
            TranslationInputFactory inputFactory,
            TranslationOutputValidator outputValidator,
            TranslationProperties properties,
            ArticleFeedCache feedCache,
            Clock clock) {
        this.articleService = articleService;
        this.provider = provider;
        this.inputFactory = inputFactory;
        this.outputValidator = outputValidator;
        this.properties = properties;
        this.feedCache = feedCache;
        this.clock = clock;
    }

    public boolean ensureTranslations(String articleId) {
        return ensureTranslations(articleId, null);
    }

    public boolean ensureTranslations(String articleId, Language onlyTarget) {
        Article article = articleService.findById(articleId)
                .orElseThrow(() -> new IllegalStateException("Article does not exist"));
        var prepared = inputFactory.prepare(article, properties);
        if (prepared.title().length() + prepared.summary().length()
                > properties.maxInputCharacters()) {
            throw new TranslationProviderException("Translation input exceeds the configured maximum.");
        }
        Set<Language> required = requiredTargets(article.originalLanguage());
        if (onlyTarget != null) {
            if (onlyTarget == article.originalLanguage()) {
                return false;
            }
            required = Set.of(onlyTarget);
        }
        Set<Language> stale = EnumSet.copyOf(required);
        stale.removeIf(language -> valid(article.translations().get(language), prepared.inputHash()));
        if (stale.isEmpty()) {
            return false;
        }

        TranslationInput input = new TranslationInput(
                article.originalLanguage(), prepared.title(), prepared.summary(), stale);
        Map<Language, TranslatedContent> output = outputValidator.validate(
                provider.translate(input), stale, !prepared.summary().isBlank(), input);
        Map<Language, ArticleTranslation> updated = new EnumMap<>(Language.class);
        updated.putAll(article.translations());
        output.forEach((language, content) -> updated.put(language, new ArticleTranslation(
                content.title(), content.summary(),
                content.model() == null ? properties.model() : content.model(),
                properties.promptVersion(), prepared.inputHash(), clock.instant(),
                content.provider() == null ? "GEMINI" : content.provider())));
        articleService.saveTranslations(article.id(), updated)
                .orElseThrow(() -> new IllegalStateException("Article disappeared during translation"));
        invalidateFeedCache(article.id());
        return true;
    }

    public boolean needsTranslation(Article article) {
        String hash = inputFactory.prepare(article, properties).inputHash();
        return requiredTargets(article.originalLanguage()).stream()
                .anyMatch(language -> !valid(article.translations().get(language), hash));
    }

    public boolean needsTranslation(Article article, Language target) {
        if (target == article.originalLanguage()) {
            return false;
        }
        String hash = inputFactory.prepare(article, properties).inputHash();
        return !valid(article.translations().get(target), hash);
    }

    public boolean valid(Article article, Language language) {
        if (language == article.originalLanguage()) {
            return false;
        }
        String hash = inputFactory.prepare(article, properties).inputHash();
        return valid(article.translations().get(language), hash);
    }

    private boolean valid(ArticleTranslation translation, String hash) {
        return translation != null
                && translation.matches(properties.model(), properties.promptVersion(), hash);
    }

    private Set<Language> requiredTargets(Language original) {
        EnumSet<Language> targets = EnumSet.allOf(Language.class);
        targets.remove(original);
        return Set.copyOf(targets);
    }

    private void invalidateFeedCache(String articleId) {
        try {
            feedCache.invalidate();
        } catch (RuntimeException exception) {
            LOGGER.warn("article_feed_cache_invalidation_failed articleId={} reason={}",
                    articleId, exception.getClass().getSimpleName());
        }
    }
}
