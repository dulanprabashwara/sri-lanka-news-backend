package lk.srilankannews.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "news.processing.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamGroupManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisStreamGroupManager.class);

    private final GroupCreator groupCreator;
    private final RedisProcessingProperties properties;

    @Autowired
    public RedisStreamGroupManager(
            StringRedisTemplate redisTemplate, RedisProcessingProperties properties) {
        this(
                (streamKey, consumerGroup, readOffset, createStream) ->
                        redisTemplate.execute((RedisCallback<String>) connection ->
                                connection.streamCommands().xGroupCreate(
                                        StringRedisSerializer.UTF_8.serialize(streamKey),
                                        consumerGroup,
                                        readOffset,
                                        createStream)),
                properties);
    }

    RedisStreamGroupManager(
            GroupCreator groupCreator, RedisProcessingProperties properties) {
        this.groupCreator = groupCreator;
        this.properties = properties;
    }

    public GroupInitializationResult ensureConsumerGroup() {
        try {
            groupCreator.create(
                    properties.streamKey(),
                    properties.consumerGroup(),
                    ReadOffset.from("0-0"),
                    true);
            LOGGER.info("article_stream_consumer_group_created stream={} group={}",
                    properties.streamKey(), properties.consumerGroup());
            return GroupInitializationResult.CREATED;
        } catch (RuntimeException exception) {
            if (RedisFailureDescription.containsCode(exception, "BUSYGROUP")) {
                LOGGER.info("article_stream_consumer_group_exists stream={} group={}",
                        properties.streamKey(), properties.consumerGroup());
                return GroupInitializationResult.EXISTING;
            }
            throw exception;
        }
    }

    enum GroupInitializationResult {
        CREATED,
        EXISTING
    }

    @FunctionalInterface
    interface GroupCreator {
        String create(
                String streamKey,
                String consumerGroup,
                ReadOffset readOffset,
                boolean createStream);
    }
}
