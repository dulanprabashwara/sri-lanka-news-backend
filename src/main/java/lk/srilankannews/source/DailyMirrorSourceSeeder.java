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
public class DailyMirrorSourceSeeder implements ApplicationRunner {

    public static final String SOURCE_SLUG = "daily-mirror";
    private static final Logger log = LoggerFactory.getLogger(DailyMirrorSourceSeeder.class);

    private final SourceService sourceService;

    public DailyMirrorSourceSeeder(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (sourceService.findBySlug(SOURCE_SLUG).isPresent()) {
            return;
        }

        try {
            sourceService.create(new CreateSourceCommand(
                    "Daily Mirror",
                    SOURCE_SLUG,
                    "https://www.dailymirror.lk",
                    Language.EN,
                    IngestionType.RSS,
                    true,
                    new lk.srilankannews.source.SourceImagePolicy(true, java.util.Set.of(
                            "www.dailymirror.lk",
                            "static.dailymirror.lk",
                            "cdn.dailymirror.lk",
                            "bmkltsly13vb.compat.objectstorage.ap-singapore-1.oraclecloud.com"))));
            log.info("Seeded source slug={}", SOURCE_SLUG);
        } catch (DuplicateSourceSlugException exception) {
            log.info("Source slug={} was seeded concurrently", SOURCE_SLUG);
        }
    }
}
