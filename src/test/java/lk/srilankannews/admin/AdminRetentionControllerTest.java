package lk.srilankannews.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import lk.srilankannews.auth.AdminAuthorization;
import lk.srilankannews.auth.SecurityConfiguration;
import lk.srilankannews.retention.RetentionMongoStorageService;
import lk.srilankannews.retention.RetentionRedisHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminRetentionController.class)
@Import({SecurityConfiguration.class, AdminAuthorization.class})
@TestPropertySource(properties = "news.admin.user-ids=admin-sub")
class AdminRetentionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean RetentionMongoStorageService mongoStorageService;
    @MockitoBean RetentionRedisHealthService redisHealthService;
    @MockitoBean Clock clock;

    private final Instant fixedNow = Instant.parse("2026-09-07T12:00:00Z");

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(fixedNow);
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/retention"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/retention")
                        .with(jwt().jwt(token -> token.subject("regular-user-sub"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifiedAdminUserAllowedAndReturnsOverviewWithoutPiiOrCredentials() throws Exception {
        RetentionMongoStorageService.MongoStorageOverview mongoOverview = new RetentionMongoStorageService.MongoStorageOverview(
                true, 1000L, 2000L, 500L, 500L, 10L, List.of());

        List<RetentionMongoStorageService.TtlIndexHealthStatus> ttlHealth = List.of(
                new RetentionMongoStorageService.TtlIndexHealthStatus("notifications", "expiresAt", "HEALTHY", "Healthy"));

        List<RetentionMongoStorageService.TtlDocumentLifecycleStatus> docHealth = List.of(
                new RetentionMongoStorageService.TtlDocumentLifecycleStatus(
                        "notifications", 100L, 80L, 20L, 0L, 0L, 0L, 0L, null, "ACTIVE PROTECTED"));

        RetentionRedisHealthService.RedisMemoryOverview memory = new RetentionRedisHealthService.RedisMemoryOverview(
                true, 1024L, "1KB", 2048L, "2KB", 10000L, "10KB", "noeviction");

        RetentionRedisHealthService.StreamHealthMetrics streamMetrics = new RetentionRedisHealthService.StreamHealthMetrics(
                "article-discovered", 100L, "1788000000000-0", null, "1788500000000-0", null, List.of(), "HEALTHY", true);

        RetentionRedisHealthService.FeedCacheAuditResult feedCache = new RetentionRedisHealthService.FeedCacheAuditResult(
                5, 100L, 300L, "ACTIVE SAMPLE AUDITED");

        RetentionRedisHealthService.SchedulerHealthStatus scheduler = new RetentionRedisHealthService.SchedulerHealthStatus(
                true, false, false, "0 0 */6 * * *", "IMPLEMENTED / MANUAL ACTIVATION REQUIRED");

        when(mongoStorageService.getStorageOverview()).thenReturn(mongoOverview);
        when(mongoStorageService.getTtlIndexHealth()).thenReturn(ttlHealth);
        when(mongoStorageService.getTtlDocumentHealth(any())).thenReturn(docHealth);
        when(redisHealthService.getMemoryOverview()).thenReturn(memory);
        when(redisHealthService.getStreamHealthOverview(any())).thenReturn(List.of(streamMetrics));
        when(redisHealthService.auditFeedCacheTtlSample()).thenReturn(feedCache);
        when(redisHealthService.getSchedulerHealthStatus()).thenReturn(scheduler);
        when(redisHealthService.detectTypedWarnings(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/retention")
                        .with(jwt().jwt(token -> token.subject("admin-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mongo.available").value(true))
                .andExpect(jsonPath("$.redisMemory.available").value(true))
                .andExpect(jsonPath("$.redisScheduler.scheduleEnabled").value(false))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("mongodb://"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("redis://"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("AVNS_"))));
    }
}
