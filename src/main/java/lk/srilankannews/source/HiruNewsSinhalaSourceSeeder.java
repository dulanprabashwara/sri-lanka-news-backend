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
public class HiruNewsSinhalaSourceSeeder implements ApplicationRunner {

    public static final String SOURCE_SLUG = "hiru-news-sinhala";
    private static final Logger log =
            LoggerFactory.getLogger(HiruNewsSinhalaSourceSeeder.class);
    private final SourceService sourceService;

    public HiruNewsSinhalaSourceSeeder(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (sourceService.findBySlug(SOURCE_SLUG).isPresent()) {
            return;
        }
        try {
            sourceService.create(new CreateSourceCommand(
                    "Hiru News",
                    SOURCE_SLUG,
                    "https://www.hirunews.lk",
                    Language.SI,
                    IngestionType.HTML,
                    true,
                    new lk.srilankannews.source.SourceImagePolicy(true, java.util.Set.of("www.hirunews.lk", "cdn.hirunews.lk"))));
            log.info("Seeded source slug={}", SOURCE_SLUG);
        } catch (DuplicateSourceSlugException exception) {
            log.info("Source slug={} was seeded concurrently", SOURCE_SLUG);
        }
    }
}
