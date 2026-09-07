package lk.srilankannews.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lk.srilankannews.retention.RedisStreamRetentionService;
import lk.srilankannews.retention.RetentionMongoStorageService;
import lk.srilankannews.retention.RetentionRedisHealthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin")
@PreAuthorize("@adminAuthorization.isAdmin(authentication)")
public class AdminRetentionController {

    private final RetentionMongoStorageService mongoStorageService;
    private final RetentionRedisHealthService redisHealthService;
    private final Clock clock;

    public AdminRetentionController(
            RetentionMongoStorageService mongoStorageService,
            RetentionRedisHealthService redisHealthService,
            Clock clock) {
        this.mongoStorageService = mongoStorageService;
        this.redisHealthService = redisHealthService;
        this.clock = clock;
    }

    @GetMapping("/retention")
    public AdminRetentionOverviewResponse retentionOverview() {
        Instant now = clock.instant();

        RetentionMongoStorageService.MongoStorageOverview mongoOverview = mongoStorageService.getStorageOverview();
        List<RetentionMongoStorageService.TtlIndexHealthStatus> ttlHealthList = mongoStorageService.getTtlIndexHealth();
        List<RetentionMongoStorageService.TtlDocumentLifecycleStatus> ttlDocHealthList = mongoStorageService.getTtlDocumentHealth(now);

        RetentionRedisHealthService.RedisMemoryOverview redisMemory = redisHealthService.getMemoryOverview();
        List<RetentionRedisHealthService.StreamHealthMetrics> streamMetrics = redisHealthService.getStreamHealthOverview(now);
        RetentionRedisHealthService.FeedCacheAuditResult feedCacheAudit = redisHealthService.auditFeedCacheTtlSample();
        RetentionRedisHealthService.SchedulerHealthStatus schedulerStatus = redisHealthService.getSchedulerHealthStatus();

        List<RetentionRedisHealthService.RetentionWarning> warnings = redisHealthService.detectTypedWarnings(
                mongoOverview, ttlHealthList, ttlDocHealthList, streamMetrics, redisMemory, schedulerStatus);

        RetentionPolicySummary policySummary = new RetentionPolicySummary(
                "180d read / 365d unread",
                "30d published / FAILED deferred",
                "90d",
                "30d",
                "365d",
                "7d ACK-safe history (article-discovered, notification-events)",
                "NO AUTOMATIC TRIM (article-discovered-dlq)",
                "30d (analytics_events.receivedAt)",
                "DEFERRED (0 docs, permanent long term candidate)",
                "PERMANENT (articles, stories, sources, preferences, bookmarks, follows, ingestion_source_settings)");

        return new AdminRetentionOverviewResponse(
                mongoOverview,
                ttlHealthList,
                ttlDocHealthList,
                redisMemory,
                streamMetrics,
                feedCacheAudit,
                schedulerStatus,
                policySummary,
                warnings,
                now);
    }

    public record AdminRetentionOverviewResponse(
            RetentionMongoStorageService.MongoStorageOverview mongo,
            List<RetentionMongoStorageService.TtlIndexHealthStatus> mongoTtlIndexHealth,
            List<RetentionMongoStorageService.TtlDocumentLifecycleStatus> mongoTtlDocumentHealth,
            RetentionRedisHealthService.RedisMemoryOverview redisMemory,
            List<RetentionRedisHealthService.StreamHealthMetrics> redisStreams,
            RetentionRedisHealthService.FeedCacheAuditResult feedCacheAudit,
            RetentionRedisHealthService.SchedulerHealthStatus redisScheduler,
            RetentionPolicySummary policySummary,
            List<RetentionRedisHealthService.RetentionWarning> warnings,
            Instant timestamp) {
    }

    public record RetentionPolicySummary(
            String notificationsPolicy,
            String notificationOutboxPolicy,
            String ingestionRunsPolicy,
            String ingestionTriggersPolicy,
            String adminAuditPolicy,
            String redisWorkStreamsPolicy,
            String redisDlqPolicy,
            String analyticsRawPolicy,
            String analyticsDailyVisitorsPolicy,
            String permanentCollectionsPolicy) {
    }
}
