package lk.srilankannews.article.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.api.PagedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(ArticleFeedCacheProperties.class)
@ConditionalOnProperty(
        name = "news.cache.article-feed.enabled", havingValue = "true", matchIfMissing = true)
public class RedisArticleFeedCache implements ArticleFeedCache {
    static final String GENERATION_KEY = "news:feed:generation";
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisArticleFeedCache.class);
    private static final TypeReference<PagedResponse<ArticleResponse>> RESPONSE_TYPE =
            new TypeReference<>() {
            };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ArticleFeedCacheProperties properties;

    public RedisArticleFeedCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            ArticleFeedCacheProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Lookup get(ArticleFeedQuery query) {
        try {
            String generation = currentGeneration();
            String key = query.cacheKey(generation);
            String cached = redis.opsForValue().get(key);
            if (cached == null) {
                LOGGER.debug("article_feed_cache_miss key={}", key);
                return Lookup.miss(generation);
            }
            PagedResponse<ArticleResponse> response =
                    objectMapper.readValue(cached, RESPONSE_TYPE);
            LOGGER.debug("article_feed_cache_hit key={}", key);
            return Lookup.hit(response, generation);
        } catch (RuntimeException | JsonProcessingException exception) {
            LOGGER.warn("article_feed_cache_read_failed reason={}",
                    exception.getClass().getSimpleName());
            return Lookup.unavailable();
        }
    }

    @Override
    public void put(
            ArticleFeedQuery query,
            String generation,
            PagedResponse<ArticleResponse> response) {
        if (generation == null) {
            return;
        }
        try {
            String key = query.cacheKey(generation);
            String serialized = objectMapper.writeValueAsString(response);
            redis.opsForValue().set(key, serialized, properties.ttl());
        } catch (RuntimeException | JsonProcessingException exception) {
            LOGGER.warn("article_feed_cache_write_failed reason={}",
                    exception.getClass().getSimpleName());
        }
    }

    @Override
    public void invalidate() {
        try {
            Long generation = redis.opsForValue().increment(GENERATION_KEY);
            if (generation == null) {
                LOGGER.warn("article_feed_cache_invalidation_failed reason=EmptyRedisResult");
                return;
            }
            LOGGER.debug("article_feed_cache_invalidated generation={}", generation);
        } catch (RuntimeException exception) {
            LOGGER.warn("article_feed_cache_invalidation_failed reason={}",
                    exception.getClass().getSimpleName());
        }
    }

    private String currentGeneration() {
        String generation = redis.opsForValue().get(GENERATION_KEY);
        return generation == null || generation.isBlank() ? "0" : generation;
    }
}
