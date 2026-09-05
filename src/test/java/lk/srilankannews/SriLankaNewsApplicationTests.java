package lk.srilankannews;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.srilankannews.config.RequestCorrelationFilter;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.source.SourceRepository;
import lk.srilankannews.story.StoryClusteringService;
import lk.srilankannews.story.StoryRepository;
import lk.srilankannews.user.UserBookmarkRepository;
import lk.srilankannews.user.UserPreferencesRepository;
import lk.srilankannews.user.UserFollowRepository;
import lk.srilankannews.ingestion.run.IngestionRunRepository;
import lk.srilankannews.ingestion.run.IngestionSourceLeaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "news.ai.gemini.api-key=test-gemini-key",
        "news.ai.gemini.model=gemini-test",
        "news.ai.gemini.prompt-version=v1",
        "news.ai.gemini.timeout=5s",
        "news.ai.gemini.max-input-characters=30000",
        "ingestion.api-key=test-ingestion-key",
        "management.health.mongo.enabled=false",
        "management.health.redis.enabled=false",
        "news.processing.redis.enabled=false",
        "news.cache.article-feed.enabled=false",
        "spring.data.redis.url=redis://localhost:6379",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class SriLankaNewsApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SourceRepository sourceRepository;

    @MockitoBean
    private ArticleRepository articleRepository;

    @MockitoBean
    private MongoDatabaseFactory mongoDatabaseFactory;

    @MockitoBean
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @MockitoBean
    private StoryRepository storyRepository;

    @MockitoBean
    private UserBookmarkRepository userBookmarkRepository;

    @MockitoBean
    private UserPreferencesRepository userPreferencesRepository;

    @MockitoBean
    private UserFollowRepository userFollowRepository;

    @MockitoBean
    private StoryClusteringService storyClusteringService;

    @MockitoBean
    private IngestionRunRepository ingestionRunRepository;

    @MockitoBean
    private IngestionSourceLeaseRepository ingestionSourceLeaseRepository;

    @MockitoBean
    private lk.srilankannews.admin.audit.AdminAuditEventRepository adminAuditEventRepository;

    @MockitoBean
    private lk.srilankannews.ingestion.settings.IngestionSourceSettingsRepository ingestionSourceSettingsRepository;

    @MockitoBean
    private lk.srilankannews.ingestion.trigger.IngestionTriggerRequestRepository ingestionTriggerRequestRepository;

    @MockitoBean
    private lk.srilankannews.analytics.AnalyticsEventRepository analyticsEventRepository;

    @MockitoBean
    private lk.srilankannews.analytics.AnalyticsDailyVisitorRepository analyticsDailyVisitorRepository;

    @MockitoBean
    private lk.srilankannews.notifications.NotificationRepository notificationRepository;

    @MockitoBean
    private lk.srilankannews.notifications.NotificationPreferenceRepository notificationPreferenceRepository;

    @MockitoBean
    private lk.srilankannews.notifications.NotificationEventRepository notificationEventRepository;

    @MockitoBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private lk.srilankannews.notifications.NotificationEventConsumer notificationEventConsumer;

    @Test
    void contextLoads() {
    }

    @Test
    void actuatorHealthExposesOnlySafeInformation() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
