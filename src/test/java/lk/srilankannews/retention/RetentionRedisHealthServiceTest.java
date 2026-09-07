package lk.srilankannews.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroup;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroups;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class RetentionRedisHealthServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private StreamOperations opsForStream;
    @Mock
    private RedisStreamRetentionService retentionService;
    @Mock
    private XInfoGroups xInfoGroups;
    @Mock
    private XInfoGroup xInfoGroup;
    @Mock
    private PendingMessagesSummary pendingSummary;

    private Clock fixedClock;
    private RetentionRedisProperties properties;
    private RetentionRedisHealthService service;

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

        service = new RetentionRedisHealthService(redis, properties, retentionService, fixedClock);
    }

    @Test
    void getMemoryOverviewParsesRedisInfoProperties() {
        Properties infoProps = new Properties();
        infoProps.setProperty("used_memory", "10485760");
        infoProps.setProperty("used_memory_human", "10.00M");
        infoProps.setProperty("used_memory_peak", "20971520");
        infoProps.setProperty("used_memory_peak_human", "20.00M");
        infoProps.setProperty("maxmemory", "1073741824");
        infoProps.setProperty("maxmemory_human", "1.00G");
        infoProps.setProperty("maxmemory_policy", "noeviction");

        when(redis.execute(any(RedisCallback.class))).thenReturn(infoProps);

        RetentionRedisHealthService.RedisMemoryOverview overview = service.getMemoryOverview();

        assertThat(overview.available()).isTrue();
        assertThat(overview.usedMemory()).isEqualTo(10485760L);
        assertThat(overview.usedMemoryHuman()).isEqualTo("10.00M");
        assertThat(overview.maxmemoryPolicy()).isEqualTo("noeviction");
    }

    @Test
    void getMemoryOverviewHandlesRedisUnavailableGracefully() {
        when(redis.execute(any(RedisCallback.class))).thenThrow(new IllegalStateException("Redis Connection Refused"));

        RetentionRedisHealthService.RedisMemoryOverview overview = service.getMemoryOverview();

        assertThat(overview.available()).isFalse();
        assertThat(overview.usedMemoryHuman()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void getStreamHealthOverviewAuditsWorkAndDlqStreams() {
        when(redis.hasKey("article-discovered")).thenReturn(true);
        when(redis.hasKey("notification-events")).thenReturn(true);
        when(redis.hasKey("article-discovered-dlq")).thenReturn(true);

        when(redis.opsForStream()).thenReturn(opsForStream);
        when(opsForStream.size("article-discovered")).thenReturn(100L);
        when(opsForStream.size("notification-events")).thenReturn(10L);
        when(opsForStream.size("article-discovered-dlq")).thenReturn(5L);

        when(retentionService.getFirstStreamEntryId(anyString())).thenReturn("1788000000000-0");
        when(retentionService.getLastStreamEntryId(anyString())).thenReturn("1788500000000-0");

        when(opsForStream.groups("article-discovered")).thenReturn(xInfoGroups);
        when(xInfoGroups.iterator()).thenAnswer(i -> List.of(xInfoGroup).iterator());
        when(xInfoGroup.groupName()).thenReturn("article-processing");
        when(xInfoGroup.consumerCount()).thenReturn(2L);

        when(opsForStream.pending(eq("article-discovered"), eq("article-processing"))).thenReturn(pendingSummary);
        when(pendingSummary.getTotalPendingMessages()).thenReturn(10L);
        when(pendingSummary.minRecordId()).thenReturn(RecordId.of("1788000000000-0"));

        List<RetentionRedisHealthService.StreamHealthMetrics> metrics = service.getStreamHealthOverview(fixedClock.instant());

        assertThat(metrics).hasSize(3);

        RetentionRedisHealthService.StreamHealthMetrics articleStream = metrics.stream()
                .filter(s -> s.streamName().equals("article-discovered"))
                .findFirst()
                .orElseThrow();
        assertThat(articleStream.xlen()).isEqualTo(100L);
        assertThat(articleStream.consumerGroups()).hasSize(1);

        RetentionRedisHealthService.StreamHealthMetrics dlqStream = metrics.stream()
                .filter(s -> s.streamName().equals("article-discovered-dlq"))
                .findFirst()
                .orElseThrow();
        assertThat(dlqStream.status()).isEqualTo("DLQ NONEMPTY");
    }

    @Test
    void detectTypedWarningsEmitsExpectedWarningCodes() {
        RetentionMongoStorageService.MongoStorageOverview mongoOverview = new RetentionMongoStorageService.MongoStorageOverview(
                true, 1000L, 2000L, 500L, 500L, 10L, List.of());

        List<RetentionMongoStorageService.TtlIndexHealthStatus> ttlHealth = List.of(
                new RetentionMongoStorageService.TtlIndexHealthStatus("notifications", "expiresAt", "MISSING", "Index Missing"));

        List<RetentionMongoStorageService.TtlDocumentLifecycleStatus> docHealth = List.of(
                new RetentionMongoStorageService.TtlDocumentLifecycleStatus(
                        "notifications", 100L, 80L, 20L, 0L, 0L, 0L, 1500L, null, "ACTIVE PROTECTED"));

        RetentionRedisHealthService.StreamHealthMetrics dlqStream = new RetentionRedisHealthService.StreamHealthMetrics(
                "article-discovered-dlq", 10L, "1788000000000-0", Duration.ofDays(5), "1788500000000-0", Duration.ofHours(1), List.of(), "DLQ NONEMPTY", true);

        RetentionRedisHealthService.RedisMemoryOverview memory = new RetentionRedisHealthService.RedisMemoryOverview(
                true, 100L, "100B", 200L, "200B", 1000L, "1000B", "noeviction");

        RetentionRedisHealthService.SchedulerHealthStatus scheduler = new RetentionRedisHealthService.SchedulerHealthStatus(
                true, false, false, "0 0 */6 * * *", "IMPLEMENTED / MANUAL ACTIVATION REQUIRED");

        List<RetentionRedisHealthService.RetentionWarning> warnings = service.detectTypedWarnings(
                mongoOverview, ttlHealth, docHealth, List.of(dlqStream), memory, scheduler);

        assertThat(warnings).extracting(RetentionRedisHealthService.RetentionWarning::warningCode)
                .contains("MONGO_TTL_INDEX_MISSING", "MONGO_EXPIRED_BACKLOG", "DLQ_NONEMPTY", "REDIS_SCHEDULER_DISABLED");
        assertThat(warnings).extracting(RetentionRedisHealthService.RetentionWarning::warningCode)
                .doesNotContain("FAILED_NOTIFICATION_EVENTS_UNBOUNDED");
    }

    @Test
    void detectTypedWarningsUsesFailedDeferredCountInsteadOfAllMissingExpiryEvents() {
        RetentionMongoStorageService.MongoStorageOverview mongoOverview = new RetentionMongoStorageService.MongoStorageOverview(
                true, 1000L, 2000L, 500L, 500L, 10L, List.of());

        RetentionRedisHealthService.RedisMemoryOverview memory = new RetentionRedisHealthService.RedisMemoryOverview(
                true, 100L, "100B", 200L, "200B", 1000L, "1000B", "noeviction");

        RetentionRedisHealthService.SchedulerHealthStatus scheduler = new RetentionRedisHealthService.SchedulerHealthStatus(
                true, false, false, "0 0 */6 * * *", "IMPLEMENTED / MANUAL ACTIVATION REQUIRED");

        List<RetentionMongoStorageService.TtlDocumentLifecycleStatus> activeOnly = List.of(
                new RetentionMongoStorageService.TtlDocumentLifecycleStatus(
                        "notification_events", 3L, 0L, 3L, 3L, 0L, 0L, 0L, null,
                        "ACTIVE_PROTECTED=3; FAILED_DEFERRED=0; UNEXPECTED_MISSING=0"));

        List<RetentionRedisHealthService.RetentionWarning> warningsZero = service.detectTypedWarnings(
                mongoOverview, List.of(), activeOnly, List.of(), memory, scheduler);

        assertThat(warningsZero).extracting(RetentionRedisHealthService.RetentionWarning::warningCode)
                .doesNotContain("FAILED_NOTIFICATION_EVENTS_UNBOUNDED");

        List<RetentionMongoStorageService.TtlDocumentLifecycleStatus> mixedActiveAndFailed = List.of(
                new RetentionMongoStorageService.TtlDocumentLifecycleStatus(
                        "notification_events", 5L, 0L, 5L, 3L, 2L, 0L, 0L, null,
                        "ACTIVE_PROTECTED=3; FAILED_DEFERRED=2; UNEXPECTED_MISSING=0"));

        List<RetentionRedisHealthService.RetentionWarning> warningsWithFailed = service.detectTypedWarnings(
                mongoOverview, List.of(), mixedActiveAndFailed, List.of(), memory, scheduler);

        assertThat(warningsWithFailed)
                .filteredOn(warning -> warning.warningCode().equals("FAILED_NOTIFICATION_EVENTS_UNBOUNDED"))
                .singleElement()
                .extracting(RetentionRedisHealthService.RetentionWarning::message)
                .asString()
                .contains("2 records");
    }

    @Test
    void detectTypedWarningsReportsUnexpectedTerminalMissingExpirySeparately() {
        RetentionMongoStorageService.MongoStorageOverview mongoOverview = new RetentionMongoStorageService.MongoStorageOverview(
                true, 1000L, 2000L, 500L, 500L, 10L, List.of());
        RetentionRedisHealthService.RedisMemoryOverview memory = new RetentionRedisHealthService.RedisMemoryOverview(
                true, 100L, "100B", 200L, "200B", 1000L, "1000B", "noeviction");
        RetentionRedisHealthService.SchedulerHealthStatus scheduler = new RetentionRedisHealthService.SchedulerHealthStatus(
                true, false, false, "0 0 */6 * * *", "IMPLEMENTED / MANUAL ACTIVATION REQUIRED");
        List<RetentionMongoStorageService.TtlDocumentLifecycleStatus> unexpectedMissing = List.of(
                new RetentionMongoStorageService.TtlDocumentLifecycleStatus(
                        "notification_events", 1L, 0L, 1L, 0L, 0L, 1L, 0L, null,
                        "ACTIVE_PROTECTED=0; FAILED_DEFERRED=0; UNEXPECTED_MISSING=1"));

        List<RetentionRedisHealthService.RetentionWarning> warnings = service.detectTypedWarnings(
                mongoOverview, List.of(), unexpectedMissing, List.of(), memory, scheduler);

        assertThat(warnings).extracting(RetentionRedisHealthService.RetentionWarning::warningCode)
                .contains("NOTIFICATION_EVENTS_UNEXPECTED_MISSING_EXPIRY")
                .doesNotContain("FAILED_NOTIFICATION_EVENTS_UNBOUNDED");
    }
}
