package lk.srilankannews.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;

public class RedisArticleStreamListener
        implements StreamListener<String, MapRecord<String, String, String>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisArticleStreamListener.class);
    private final ArticleEventConsumer consumer;

    public RedisArticleStreamListener(ArticleEventConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            consumer.consume(
                    message.getId().getValue(),
                    ArticleDiscoveredEvent.fromFields(message.getValue()));
        } catch (RuntimeException exception) {
            LOGGER.error("article_event_listener_failed recordId={} reason={}",
                    message.getId().getValue(), exception.getClass().getSimpleName());
        }
    }
}
