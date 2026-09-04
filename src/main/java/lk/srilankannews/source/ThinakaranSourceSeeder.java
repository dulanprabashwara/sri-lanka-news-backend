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
public class ThinakaranSourceSeeder implements ApplicationRunner {

    public static final String SOURCE_SLUG = "thinakaran";
    private static final Logger log = LoggerFactory.getLogger(ThinakaranSourceSeeder.class);

    private final SourceService sourceService;

    public ThinakaranSourceSeeder(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (sourceService.findBySlug(SOURCE_SLUG).isPresent()) {
            return;
        }

        try {
            sourceService.create(new CreateSourceCommand(
                    "Thinakaran",
                    SOURCE_SLUG,
                    "https://www.thinakaran.lk",
                    Language.TA,
                    IngestionType.HTML,
                    false,
                    new lk.srilankannews.source.SourceImagePolicy(false, java.util.Set.of())));
            log.info("Seeded source slug={}", SOURCE_SLUG);
        } catch (DuplicateSourceSlugException exception) {
            log.info("Source slug={} was seeded concurrently", SOURCE_SLUG);
        }
    }
}
