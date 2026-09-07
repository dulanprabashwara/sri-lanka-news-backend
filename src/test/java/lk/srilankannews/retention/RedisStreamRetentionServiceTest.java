package lk.srilankannews.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroup;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroups;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class RedisStreamRetentionServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private StreamOperations opsForStream;
    @Mock
    private XInfoGroups xInfoGroups;
    @Mock
    private XInfoGroup xInfoGroup;
    @Mock
    private MapRecord mapRecord;

    private Clock fixedClock;
    private RetentionRedisProperties properties;
    private RedisStreamRetentionService service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneId.of("UTC"));
        properties = new RetentionRedisProperties(
                true,
                false,
                false,
                "0 0 */6 * * *",
                new RetentionRedisProperties.StreamRetentionConfig(7),
                new RetentionRedisProperties.StreamRetentionConfig(7),
                new RetentionRedisProperties.DlqRetentionConfig(90, false));

        service = new RedisStreamRetentionService(redis, properties, fixedClock);
    }

    @Test
    void skipsNullOrBlankStreamKeys() {
        RedisStreamRetentionResult resultNull = service.processStream(null, false);
        assertThat(resultNull.applied()).isFalse();
        assertThat(resultNull.reason()).contains("NULL OR BLANK");

        RedisStreamRetentionResult resultBlank = service.processStream("   ", false);
        assertThat(resultBlank.applied()).isFalse();
        assertThat(resultBlank.reason()).contains("NULL OR BLANK");
    }

    @Test
    void skipsDlqStreamsFromAutomaticTrimming() {
        RedisStreamRetentionResult result = service.processStream("article-discovered-dlq", false);
        assertThat(result.applied()).isFalse();
        assertThat(result.reason()).contains("DLQ STREAM CANNOT BE AUTOMATICALLY TRIMMED");
    }

    @Test
    void returnsPreviewModeResultWhenApplyIsFalse() {
        when(redis.hasKey("article-discovered")).thenReturn(true);
        when(redis.opsForStream()).thenReturn(opsForStream);
        when(opsForStream.size("article-discovered")).thenReturn(500L);

        when(opsForStream.range(eq("article-discovered"), any(Range.class), any(Limit.class)))
                .thenReturn(List.of(mapRecord));
        when(mapRecord.getId()).thenReturn(RecordId.of("1788000000000-0"));

        when(opsForStream.groups("article-discovered")).thenReturn(xInfoGroups);
        when(xInfoGroups.isEmpty()).thenReturn(false);
        when(xInfoGroups.iterator()).thenAnswer(invocation -> List.of(xInfoGroup).iterator());
        when(xInfoGroup.groupName()).thenReturn("article-processing");
        when(xInfoGroup.lastDeliveredId()).thenReturn("1788500000000-0");

        RedisStreamRetentionResult result = service.processStream("article-discovered", false);

        assertThat(result.safeToTrim()).isTrue();
        assertThat(result.applied()).isFalse();
        assertThat(result.reason()).contains("PREVIEW MODE — OK");
    }

    @Test
    void getFirstStreamEntryIdUsesBoundedCountOneQuery() {
        when(redis.opsForStream()).thenReturn(opsForStream);
        when(opsForStream.range(eq("article-discovered"), any(Range.class), any(Limit.class)))
                .thenReturn(List.of(mapRecord));
        when(mapRecord.getId()).thenReturn(RecordId.of("1788000000000-0"));

        String firstId = service.getFirstStreamEntryId("article-discovered");

        assertThat(firstId).isEqualTo("1788000000000-0");
        verify(opsForStream).range(eq("article-discovered"), any(Range.class), any(Limit.class));
    }

    @Test
    void getLastStreamEntryIdUsesBoundedCountOneReverseQuery() {
        when(redis.opsForStream()).thenReturn(opsForStream);
        when(opsForStream.reverseRange(eq("article-discovered"), any(Range.class), any(Limit.class)))
                .thenReturn(List.of(mapRecord));
        when(mapRecord.getId()).thenReturn(RecordId.of("1788500000000-0"));

        String lastId = service.getLastStreamEntryId("article-discovered");

        assertThat(lastId).isEqualTo("1788500000000-0");
        verify(opsForStream).reverseRange(eq("article-discovered"), any(Range.class), any(Limit.class));
    }
}
