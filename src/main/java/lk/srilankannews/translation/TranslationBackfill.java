package lk.srilankannews.translation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.SourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class TranslationBackfill implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationBackfill.class);
    private static final int DEFAULT_BATCH_SIZE = 20;
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_ARTICLES = 10_000;

    private final ArticleRepository repository;
    private final ArticleTranslationService translationService;
    private final TranslationProperties properties;
    private final SourceService sourceService;

    @Autowired
    public TranslationBackfill(
            ArticleRepository repository,
            ArticleTranslationService translationService,
            TranslationProperties properties,
            SourceService sourceService) {
        this.repository = repository;
        this.translationService = translationService;
        this.properties = properties;
        this.sourceService = sourceService;
    }

    TranslationBackfill(
            ArticleRepository repository,
            ArticleTranslationService translationService,
            TranslationProperties properties) {
        this(repository, translationService, properties, null);
    }

    @Override
    public void run(ApplicationArguments args) {
        Options options = options(args);
        if (!options.enabled()) {
            LOGGER.info("multilingual_backfill_disabled");
            return;
        }

        String sourceId = resolveSource(options.source());
        Map<Language, Integer> missing = new EnumMap<>(Language.class);
        int scanned = 0;
        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        int page = 0;
        boolean finished = false;

        while (!finished && scanned < options.maxArticles()) {
            List<Article> articles = repository.findAll(PageRequest.of(
                    page++, options.batchSize(),
                    Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")))).getContent();
            if (articles.isEmpty()) {
                break;
            }
            for (Article article : articles) {
                if (!matches(article, sourceId, options)) {
                    continue;
                }
                scanned++;
                List<Language> targets = targets(article, options.targetLanguage());
                for (Language target : targets) {
                    if (!translationService.needsTranslation(article, target)) {
                        continue;
                    }
                    missing.merge(target, 1, Integer::sum);
                    if (!options.dryRun()) {
                        attempted++;
                        try {
                            if (translationService.ensureTranslations(article.id(), target)) {
                                succeeded++;
                            }
                        } catch (RuntimeException exception) {
                            failed++;
                            LOGGER.warn("multilingual_backfill_failed articleId={} target={} reason={}",
                                    article.id(), target, exception.getClass().getSimpleName());
                        }
                    }
                }
                if (scanned >= options.maxArticles()) {
                    finished = true;
                    break;
                }
            }
            if (articles.size() < options.batchSize()) {
                break;
            }
        }

        LOGGER.info(
                "multilingual_backfill_complete dryRun={} scanned={} missingEN={} missingSI={} missingTA={} attempted={} succeeded={} failed={}",
                options.dryRun(), scanned, missing.getOrDefault(Language.EN, 0),
                missing.getOrDefault(Language.SI, 0), missing.getOrDefault(Language.TA, 0),
                attempted, succeeded, failed);
    }

    private Options options(ApplicationArguments args) {
        if (args == null) {
            int legacyLimit = Math.min(properties.backfillLimit(), MAX_ARTICLES);
            return new Options(legacyLimit > 0, false, DEFAULT_BATCH_SIZE,
                    legacyLimit, null, null, null, null);
        }
        boolean explicitlyEnabled = args.containsOption("translation-backfill");
        if (!explicitlyEnabled) {
            return Options.disabled();
        }
        int batchSize = boundedInt(args, "batch-size", DEFAULT_BATCH_SIZE, 1, MAX_BATCH_SIZE);
        int maxArticles = boundedInt(args, "max-articles", 100, 1, MAX_ARTICLES);
        boolean dryRun = !args.containsOption("apply") || args.containsOption("dry-run");
        return new Options(true, dryRun, batchSize, maxArticles,
                option(args, "source"), instant(args, "from-date", false),
                instant(args, "to-date", true), language(args, "target-language"));
    }

    private boolean matches(Article article, String sourceId, Options options) {
        return (sourceId == null || sourceId.equals(article.sourceId()))
                && (options.fromDate() == null || !article.publishedAt().isBefore(options.fromDate()))
                && (options.toDate() == null || article.publishedAt().isBefore(options.toDate()));
    }

    private List<Language> targets(Article article, Language target) {
        if (target != null) {
            return target == article.originalLanguage() ? List.of() : List.of(target);
        }
        return java.util.Arrays.stream(Language.values())
                .filter(language -> language != article.originalLanguage())
                .toList();
    }

    private String resolveSource(String slug) {
        if (slug == null) {
            return null;
        }
        if (sourceService == null) {
            throw new IllegalStateException("Source filtering is unavailable.");
        }
        return sourceService.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Unknown source slug."))
                .id();
    }

    private int boundedInt(
            ApplicationArguments args, String name, int fallback, int minimum, int maximum) {
        String value = option(args, name);
        if (value == null) {
            return fallback;
        }
        int parsed = Integer.parseInt(value);
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(name + " is outside the allowed range.");
        }
        return parsed;
    }

    private String option(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private Instant instant(ApplicationArguments args, String name, boolean exclusiveEnd) {
        String value = option(args, name);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException ignored) {
            LocalDate date = LocalDate.parse(value);
            if (exclusiveEnd) {
                date = date.plusDays(1);
            }
            return date.atStartOfDay().toInstant(ZoneOffset.UTC);
        }
    }

    private Language language(ApplicationArguments args, String name) {
        String value = option(args, name);
        return value == null ? null : Language.fromCode(value);
    }

    private record Options(
            boolean enabled,
            boolean dryRun,
            int batchSize,
            int maxArticles,
            String source,
            Instant fromDate,
            Instant toDate,
            Language targetLanguage) {
        static Options disabled() {
            return new Options(false, true, DEFAULT_BATCH_SIZE, 0,
                    null, null, null, null);
        }
    }
}
