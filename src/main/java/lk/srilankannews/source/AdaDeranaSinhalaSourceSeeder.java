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
public class AdaDeranaSinhalaSourceSeeder implements ApplicationRunner {

    public static final String SOURCE_SLUG = "ada-derana-sinhala";
    private static final Logger log =
            LoggerFactory.getLogger(AdaDeranaSinhalaSourceSeeder.class);
    private final SourceService sourceService;

    public AdaDeranaSinhalaSourceSeeder(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (sourceService.findBySlug(SOURCE_SLUG).isPresent()) {
            sourceService.setEnabledBySlug(SOURCE_SLUG, false);
            return;
        }
        try {
            sourceService.create(new CreateSourceCommand(
                    "Ada Derana Sinhala",
                    SOURCE_SLUG,
                    "https://sinhala.adaderana.lk",
                    Language.SI,
                    IngestionType.RSS,
                    false,
                    new lk.srilankannews.source.SourceImagePolicy(false, java.util.Set.of())));
            log.info("Seeded source slug={}", SOURCE_SLUG);
        } catch (DuplicateSourceSlugException exception) {
            log.info("Source slug={} was seeded concurrently", SOURCE_SLUG);
        }
    }
}
