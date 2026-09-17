package lk.srilankannews.source;

import java.util.Set;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.ingestion.settings.IngestionSourceSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class LakbimaNewsSourceSeeder implements ApplicationRunner {

    public static final String SOURCE_SLUG = "lakbima-news";
    private static final Logger log = LoggerFactory.getLogger(LakbimaNewsSourceSeeder.class);
    private final SourceService sourceService;
    private final IngestionSourceSettingsService settingsService;

    public LakbimaNewsSourceSeeder(
            SourceService sourceService,
            IngestionSourceSettingsService settingsService) {
        this.sourceService = sourceService;
        this.settingsService = settingsService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (sourceService.findBySlug(SOURCE_SLUG).isEmpty()) {
            try {
                sourceService.create(new CreateSourceCommand(
                        "Lakbima News",
                        SOURCE_SLUG,
                        "https://lakbima.news",
                        Language.SI,
                        IngestionType.RSS,
                        true,
                        new SourceImagePolicy(true, Set.of("lakbima.news", "www.lakbima.news"))));
                log.info("Seeded source slug={}", SOURCE_SLUG);
            } catch (DuplicateSourceSlugException exception) {
                log.info("Source slug={} was seeded concurrently", SOURCE_SLUG);
            }
        }

        settingsService.ensureDefaults(SOURCE_SLUG, true, 15, 120);
        settingsService.disableIfPresent(HiruNewsSinhalaSourceSeeder.SOURCE_SLUG);
    }
}
