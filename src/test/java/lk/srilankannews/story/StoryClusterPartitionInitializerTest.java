package lk.srilankannews.story;

import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class StoryClusterPartitionInitializerTest {

    @Test
    void createsAllCurrentLanguagePartitionsBeforeProcessingStarts() throws Exception {
        StoryClusterPartitionStore store =
                org.mockito.Mockito.mock(StoryClusterPartitionStore.class);
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        StoryClusterPartitionInitializer initializer = new StoryClusterPartitionInitializer(
                store, Clock.fixed(now, ZoneOffset.UTC));

        initializer.run(new DefaultApplicationArguments());

        verify(store).ensureExists("lexical-v1:EN", now);
        verify(store).ensureExists("lexical-v1:SI", now);
        verify(store).ensureExists("lexical-v1:TA", now);
    }
}
