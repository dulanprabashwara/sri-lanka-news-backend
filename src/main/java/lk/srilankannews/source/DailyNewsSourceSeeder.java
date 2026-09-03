package lk.srilankannews.source;

import lk.srilankannews.common.domain.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DailyNewsSourceSeeder implements ApplicationRunner {

    public static final String SOURCE_SLUG = "daily-news";
    private static final Logger log = LoggerFactory.getLogger(DailyNewsSourceSeeder.class);

    private final SourceService sourceService;

    public DailyNewsSourceSeeder(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (sourceService.findBySlug(SOURCE_SLUG).isPresent()) {
            return;
        }

        try {
            sourceService.create(new CreateSourceCommand(
                    "Daily News",
                    SOURCE_SLUG,
                    "https://www.dailynews.lk",
                    Language.EN,
                    IngestionType.HTML, // Blocked, so type doesn't matter much
                    false));
            log.info("Seeded source slug={}", SOURCE_SLUG);
        } catch (DuplicateSourceSlugException exception) {
            log.info("Source slug={} was seeded concurrently", SOURCE_SLUG);
        }
    }
}
