package lk.srilankannews.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "news.retention.backfill.enabled", havingValue = "true")
public class RetentionBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RetentionBackfillRunner.class);

    private final RetentionBackfillService backfillService;

    public RetentionBackfillRunner(RetentionBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Override
    public void run(String... args) {
        log.info("RetentionBackfillRunner triggered by news.retention.backfill.enabled=true");
        backfillService.performBackfill();
    }
}
