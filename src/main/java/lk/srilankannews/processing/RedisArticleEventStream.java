package lk.srilankannews.processing;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisArticleEventStream implements ArticleEventStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisArticleEventStream.class);
    private final StringRedisTemplate redis;
    private final RedisProcessingProperties properties;
    private final Clock clock;

    public RedisArticleEventStream(
            StringRedisTemplate redis, RedisProcessingProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean publish(ArticleDiscoveredEvent event) {
        return add(properties.streamKey(), event.toFields(), event);
    }

    @Override
    public boolean publishDeadLetter(ArticleDiscoveredEvent event, String reason) {
        Map<String, String> fields = new LinkedHashMap<>(event.toFields());
        fields.put("failedAt", clock.instant().toString());
        fields.put("failureReason", reason.substring(0, Math.min(reason.length(), 500)));
        return add(properties.deadLetterStreamKey(), fields, event);
    }

    @Override
    public void acknowledge(String recordId) {
        redis.opsForStream().acknowledge(
                properties.streamKey(), properties.consumerGroup(), RecordId.of(recordId));
    }

    private boolean add(String stream, Map<String, String> fields, ArticleDiscoveredEvent event) {
        try {
            RecordId recordId = redis.opsForStream()
                    .add(StreamRecords.mapBacked(fields).withStreamKey(stream));
            if (recordId == null) {
                return false;
            }
            LOGGER.info("article_event_published stream={} eventId={} articleId={} attempt={}",
                    stream, event.eventId(), event.articleId(), event.attempt());
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn("article_event_publish_failed stream={} eventId={} articleId={} reason={}",
                    stream, event.eventId(), event.articleId(),
                    exception.getClass().getSimpleName());
            return false;
        }
    }
}
