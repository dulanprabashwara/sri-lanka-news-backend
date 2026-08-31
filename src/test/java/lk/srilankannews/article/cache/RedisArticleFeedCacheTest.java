package lk.srilankannews.article.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.api.SourceSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisArticleFeedCacheTest {
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    private RedisArticleFeedCache cache;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        cache = new RedisArticleFeedCache(
                redis,
                objectMapper,
                new ArticleFeedCacheProperties(true, Duration.ofSeconds(60)));
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void missCanBeWrittenWithConfiguredTtlThenReadAsPublicDto() throws Exception {
        ArticleFeedQuery query = query();
        PagedResponse<ArticleResponse> response = response();
        String key = query.cacheKey("0");
        when(values.get(RedisArticleFeedCache.GENERATION_KEY)).thenReturn(null);
        when(values.get(key)).thenReturn(null);

        ArticleFeedCache.Lookup miss = cache.get(query);
        cache.put(query, miss.generation(), response);

        assertThat(miss.response()).isEmpty();
        verify(values).set(key, objectMapper.writeValueAsString(response), Duration.ofSeconds(60));

        when(values.get(key)).thenReturn(objectMapper.writeValueAsString(response));
        ArticleFeedCache.Lookup hit = cache.get(query);
        assertThat(hit.response()).contains(response);
    }

    @Test
    void cachedJsonContainsOnlyPublicArticleFields() {
        ArticleFeedQuery query = query();
        cache.put(query, "3", response());

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(
                org.mockito.ArgumentMatchers.eq(query.cacheKey("3")),
                json.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(60)));
        assertThat(json.getValue())
                .doesNotContain("extractedContent")
                .doesNotContain("contentHash")
                .doesNotContain("processingStatus")
                .doesNotContain("promptVersion")
                .doesNotContain("gemini-test");
    }

    @Test
    void redisReadAndWriteFailuresDoNotEscape() {
        when(values.get(RedisArticleFeedCache.GENERATION_KEY))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThat(cache.get(query()).available()).isFalse();
        assertThatCode(() -> cache.put(query(), "0", response())).doesNotThrowAnyException();
    }

    @Test
    void incrementsGenerationAndSwallowsInvalidationFailure() {
        when(values.increment(RedisArticleFeedCache.GENERATION_KEY)).thenReturn(4L);
        cache.invalidate();
        verify(values).increment(RedisArticleFeedCache.GENERATION_KEY);

        when(values.increment(RedisArticleFeedCache.GENERATION_KEY))
                .thenThrow(new IllegalStateException("redis unavailable"));
        assertThatCode(cache::invalidate).doesNotThrowAnyException();
    }

    private ArticleFeedQuery query() {
        return new ArticleFeedQuery(0, 20, null, null, null, Sort.Direction.DESC);
    }

    private PagedResponse<ArticleResponse> response() {
        ArticleResponse article = new ArticleResponse(
                "article-1",
                "Public title",
                "https://publisher.example/article",
                Language.EN,
                List.of("Reporter"),
                Instant.parse("2026-08-31T00:00:00Z"),
                Instant.parse("2026-08-31T00:01:00Z"),
                ArticleCategory.LOCAL,
                "Public summary",
                List.of("Sri Lanka"),
                new SourceSummaryResponse(
                        "Publisher", "publisher", "https://publisher.example"));
        return new PagedResponse<>(List.of(article), 0, 20, 1, 1, true, true);
    }
}
