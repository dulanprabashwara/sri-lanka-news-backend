package lk.srilankannews.story;

import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
class StoryClusterPartitionInitializer implements ApplicationRunner {
    private final StoryClusterPartitionStore store;
    private final Clock clock;

    StoryClusterPartitionInitializer(StoryClusterPartitionStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        store.ensureExists(StoryClusterPartition.activeId(), clock.instant());
    }
}
