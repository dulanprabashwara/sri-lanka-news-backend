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
public class NewswireSourceSeeder implements ApplicationRunner {

    public static final String SOURCE_SLUG = "newswire";
    private static final Logger log = LoggerFactory.getLogger(NewswireSourceSeeder.class);

    private final SourceService sourceService;

    public NewswireSourceSeeder(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (sourceService.findBySlug(SOURCE_SLUG).isPresent()) {
            return;
        }

        try {
            sourceService.create(new CreateSourceCommand(
                    "Newswire",
                    SOURCE_SLUG,
                    "https://www.newswire.lk",
                    Language.EN,
                    IngestionType.RSS,
                    true,
                    new SourceImagePolicy(true, java.util.Set.of("www.newswire.lk"))));
            log.info("Seeded source slug={}", SOURCE_SLUG);
        } catch (DuplicateSourceSlugException exception) {
            log.info("Source slug={} was seeded concurrently", SOURCE_SLUG);
        }
    }
}
