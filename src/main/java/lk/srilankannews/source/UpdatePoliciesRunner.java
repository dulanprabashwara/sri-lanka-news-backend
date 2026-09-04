package lk.srilankannews.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
@Profile("!test")
public class UpdatePoliciesRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UpdatePoliciesRunner.class);
    private final SourceRepository sourceRepository;

    public UpdatePoliciesRunner(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        sourceRepository.findBySlug("daily-mirror").ifPresent(s -> 
            sourceRepository.save(new Source(s.id(), s.name(), s.slug(), s.baseUrl(), s.defaultLanguage(), s.ingestionType(), s.enabled(), new SourceImagePolicy(true, Set.of("www.dailymirror.lk", "static.dailymirror.lk", "cdn.dailymirror.lk")), s.createdAt(), Instant.now()))
        );
        sourceRepository.findBySlug("newsfirst").ifPresent(s -> 
            sourceRepository.save(new Source(s.id(), s.name(), s.slug(), s.baseUrl(), s.defaultLanguage(), s.ingestionType(), s.enabled(), new SourceImagePolicy(true, Set.of("www.newsfirst.lk", "cdn.newsfirst.lk")), s.createdAt(), Instant.now()))
        );
        sourceRepository.findBySlug("hiru-news-sinhala").ifPresent(s -> 
            sourceRepository.save(new Source(s.id(), s.name(), s.slug(), s.baseUrl(), s.defaultLanguage(), s.ingestionType(), s.enabled(), new SourceImagePolicy(true, Set.of("www.hirunews.lk", "cdn.hirunews.lk")), s.createdAt(), Instant.now()))
        );
        sourceRepository.findBySlug("the-island").ifPresent(s -> 
            sourceRepository.save(new Source(s.id(), s.name(), s.slug(), s.baseUrl(), s.defaultLanguage(), s.ingestionType(), s.enabled(), new SourceImagePolicy(true, Set.of("island.lk", "www.island.lk")), s.createdAt(), Instant.now()))
        );
        sourceRepository.findBySlug("lankadeepa").ifPresent(s -> 
            sourceRepository.save(new Source(s.id(), s.name(), s.slug(), s.baseUrl(), s.defaultLanguage(), s.ingestionType(), s.enabled(), new SourceImagePolicy(true, Set.of("www.lankadeepa.lk", "cdn.lankadeepa.lk")), s.createdAt(), Instant.now()))
        );
        sourceRepository.findBySlug("divaina").ifPresent(s -> 
            sourceRepository.save(new Source(s.id(), s.name(), s.slug(), s.baseUrl(), s.defaultLanguage(), s.ingestionType(), s.enabled(), new SourceImagePolicy(true, Set.of("divaina.lk", "www.divaina.lk")), s.createdAt(), Instant.now()))
        );
        log.info("Updated all image policies");
    }
}
