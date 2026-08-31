package lk.srilankannews.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RedisStreamGroupManagerTest {

    @Test
    void createsMissingStreamAndConsumerGroupWithMkStream() {
        AtomicBoolean mkStream = new AtomicBoolean();
        RedisStreamGroupManager manager = manager((stream, group, offset, createStream) -> {
            assertThat(stream).isEqualTo("article-events");
            assertThat(group).isEqualTo("article-workers");
            assertThat(offset.getOffset()).isEqualTo("0-0");
            mkStream.set(createStream);
            return "OK";
        });

        assertThat(manager.ensureConsumerGroup())
                .isEqualTo(RedisStreamGroupManager.GroupInitializationResult.CREATED);
        assertThat(mkStream).isTrue();
    }

    @Test
    void createsConsumerGroupWhenStreamAlreadyExists() {
        AtomicBoolean invoked = new AtomicBoolean();
        RedisStreamGroupManager manager = manager((stream, group, offset, createStream) -> {
            invoked.set(true);
            assertThat(createStream).isTrue();
            return "OK";
        });

        assertThat(manager.ensureConsumerGroup())
                .isEqualTo(RedisStreamGroupManager.GroupInitializationResult.CREATED);
        assertThat(invoked).isTrue();
    }

    @Test
    void treatsWrappedBusyGroupAsExistingConsumerGroup() {
        RedisStreamGroupManager manager = manager((stream, group, offset, createStream) -> {
            throw new RuntimeException(
                    "Redis command failed",
                    new RuntimeException("BUSYGROUP Consumer Group name already exists"));
        });

        assertThat(manager.ensureConsumerGroup())
                .isEqualTo(RedisStreamGroupManager.GroupInitializationResult.EXISTING);
    }

    @Test
    void propagatesNonBusyGroupRedisFailures() {
        RedisStreamGroupManager manager = manager((stream, group, offset, createStream) -> {
            throw new RuntimeException("Connection refused");
        });

        assertThatThrownBy(manager::ensureConsumerGroup)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Connection refused");
    }

    private RedisStreamGroupManager manager(RedisStreamGroupManager.GroupCreator creator) {
        return new RedisStreamGroupManager(creator, properties());
    }

    private RedisProcessingProperties properties() {
        return new RedisProcessingProperties(
                true,
                "article-events",
                "article-events-dead-letter",
                "article-workers",
                "backend-1",
                3,
                Duration.ofSeconds(1));
    }
}
