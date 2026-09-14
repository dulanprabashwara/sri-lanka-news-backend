package lk.srilankannews.notifications;

import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.retention.RetentionPolicyService;
import lk.srilankannews.retention.RetentionProperties;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import lk.srilankannews.user.FollowTargetType;
import lk.srilankannews.user.TopicNormalizer;
import lk.srilankannews.user.UserFollow;
import lk.srilankannews.user.UserFollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class NotificationProcessingServiceTest {

    private NotificationRepository notificationRepository;
    private NotificationEventRepository eventRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private UserFollowRepository followRepository;
    private StoryRepository storyRepository;
    private ArticleService articleService;
    private SourceService sourceService;
    private RetentionPolicyService retentionPolicyService;
    private TopicNormalizer topicNormalizer;
    private Clock clock;
    private lk.srilankannews.analytics.AnalyticsRecorder analyticsRecorder;
    private NotificationProcessingService service;

    private final Instant NOW = Instant.parse("2026-09-04T10:00:00Z");

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        eventRepository = mock(NotificationEventRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        followRepository = mock(UserFollowRepository.class);
        storyRepository = mock(StoryRepository.class);
        articleService = mock(ArticleService.class);
        sourceService = mock(SourceService.class);
        analyticsRecorder = mock(lk.srilankannews.analytics.AnalyticsRecorder.class);
        retentionPolicyService = new RetentionPolicyService(RetentionProperties.defaults());
        topicNormalizer = new TopicNormalizer();
        clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        
        service = new NotificationProcessingService(
                articleService,
                storyRepository,
                sourceService,
                followRepository,
                preferenceRepository,
                notificationRepository,
                eventRepository,
                analyticsRecorder,
                retentionPolicyService,
                topicNormalizer,
                clock
        );
    }

    @Test
    void shouldCreateNotificationWhenMatchingFollowedTopic() {
        String userId = "user-123";
        String sourceId = "daily-mirror";
        String topicId = "politics";
        
        NotificationEvent event = new NotificationEvent(
                "event-1", "article-100", "story-200", sourceId, Instant.now(clock), "v1", 
                NotificationEvent.EventStatus.PENDING, 0, Instant.now(clock), Instant.now(clock), null, null, null
        );
        when(eventRepository.findById("event-1")).thenReturn(Optional.of(event));
        
        Map<String, String> payload = Map.of(
            "eventId", "event-1",
            "articleId", "article-100",
            "storyId", "story-200",
            "eventVersion", "v1"
        );
        
        Article article = mock(Article.class);
        when(article.id()).thenReturn("article-100");
        when(article.sourceId()).thenReturn(sourceId);
        when(article.title()).thenReturn("Article Title");
        lk.srilankannews.article.ArticleAiEnrichment enrichment = mock(lk.srilankannews.article.ArticleAiEnrichment.class);
        when(enrichment.topics()).thenReturn(List.of(topicId));
        when(enrichment.summary()).thenReturn("Article summary");
        when(article.aiEnrichment()).thenReturn(enrichment);
        when(articleService.findById("article-100")).thenReturn(Optional.of(article));
        
        UserFollow ufTopic = new UserFollow(null, userId, lk.srilankannews.user.FollowTargetType.TOPIC, topicId, null, null);
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(eq(FollowTargetType.TOPIC), argThat(c -> c != null && c.contains(topicId))))
                .thenReturn(List.of(ufTopic));
        
        NotificationPreference prefs = new NotificationPreference(
                userId, true, "user@example.com", true, true, true, false, 
                false, null, null, "UTC", Instant.now(clock), Instant.now(clock)
        );
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(prefs));
        
        Story story = mock(Story.class);
        when(story.id()).thenReturn("story-200");
        when(storyRepository.findById("story-200")).thenReturn(Optional.of(story));

        // Act
        service.processEvent(payload);

        // Assert
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(notificationCaptor.capture());
        
        Notification captured = notificationCaptor.getValue();
        assertThat(captured.userId()).isEqualTo(userId);
        assertThat(captured.emailDelivery().status())
                .isEqualTo(Notification.EmailDelivery.DeliveryStatus.PENDING);
        assertThat(captured.emailDelivery().nextAttemptAt()).isEqualTo(NOW);
        assertThat(captured.expiresAt()).isNotNull(); // Unread notification expires after 365 days
        assertThat(captured.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(365)));
        
        assertThat(captured.reasons()).containsExactly(Notification.NotificationReason.FOLLOWED_TOPIC);
    }

    @Test
    void shouldNeverCreateNotificationForFollowedSourceAlone() {
        String userId = "user-123";
        String sourceId = "daily-mirror";

        NotificationEvent event = new NotificationEvent(
                "event-source-only", "art-src", "st-src", sourceId, Instant.now(clock), "v1",
                NotificationEvent.EventStatus.PENDING, 0, Instant.now(clock), Instant.now(clock), null, null, null
        );
        when(eventRepository.findById("event-source-only")).thenReturn(Optional.of(event));

        Map<String, String> payload = Map.of(
            "eventId", "event-source-only",
            "articleId", "art-src",
            "storyId", "st-src",
            "eventVersion", "v1"
        );

        Article article = mock(Article.class);
        when(article.id()).thenReturn("art-src");
        when(article.sourceId()).thenReturn(sourceId);
        when(article.aiEnrichment()).thenReturn(null); // No topics
        when(articleService.findById("art-src")).thenReturn(Optional.of(article));

        Story story = mock(Story.class);
        when(story.id()).thenReturn("st-src");
        when(storyRepository.findById("st-src")).thenReturn(Optional.of(story));

        // Act
        service.processEvent(payload);

        // Assert: NO notification is saved
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldRespectPreferencesAndSuppressWhenDisabled() {
        String userId = "user-123";
        String topicId = "cricket";
        NotificationEvent event = new NotificationEvent(
                "event-2", "art1", "st1", "src1", Instant.now(clock), "v1", 
                NotificationEvent.EventStatus.PENDING, 0, Instant.now(clock), Instant.now(clock), null, null, null
        );
        when(eventRepository.findById("event-2")).thenReturn(Optional.of(event));
        
        Map<String, String> payload = Map.of(
            "eventId", "event-2",
            "articleId", "art1",
            "storyId", "st1",
            "eventVersion", "v1"
        );
        
        Article article = mock(Article.class);
        when(article.id()).thenReturn("art1");
        when(article.sourceId()).thenReturn("src1");
        lk.srilankannews.article.ArticleAiEnrichment enrichment = mock(lk.srilankannews.article.ArticleAiEnrichment.class);
        when(enrichment.topics()).thenReturn(List.of(topicId));
        when(article.aiEnrichment()).thenReturn(enrichment);
        when(articleService.findById("art1")).thenReturn(Optional.of(article));
        
        Story story = mock(Story.class);
        when(story.id()).thenReturn("st1");
        when(storyRepository.findById("st1")).thenReturn(Optional.of(story));
        
        UserFollow ufTopic = new UserFollow(null, userId, lk.srilankannews.user.FollowTargetType.TOPIC, topicId, null, null);
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(eq(FollowTargetType.TOPIC), argThat(c -> c != null && c.contains(topicId)))).thenReturn(List.of(ufTopic));
        
        NotificationPreference prefs = new NotificationPreference(
                userId, true, "test@test.com", true, 
                true,
                false, // topicFollowNotificationsEnabled = false
                false, false, null, null, "UTC", Instant.now(clock), Instant.now(clock)
        );
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(prefs));

        // Act
        service.processEvent(payload);

        // Assert
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldDeferEmailDeliveryDuringCrossMidnightQuietHoursInUserTimezone() {
        String userId = "user-qh";
        String topicId = "cricket";
        // 18:00:00 UTC = 23:30:00 Asia/Colombo (within 22:00 -> 07:00 quiet hours)
        Instant colomboNight = Instant.parse("2026-09-04T18:00:00Z");
        Clock qhClock = Clock.fixed(colomboNight, ZoneId.of("UTC"));
        NotificationProcessingService qhService = new NotificationProcessingService(
                articleService,
                storyRepository,
                sourceService,
                followRepository,
                preferenceRepository,
                notificationRepository,
                eventRepository,
                analyticsRecorder,
                retentionPolicyService,
                topicNormalizer,
                qhClock
        );

        NotificationEvent event = new NotificationEvent(
                "event-qh", "art1", "st1", "src1", colomboNight, "v1",
                NotificationEvent.EventStatus.PENDING, 0, colomboNight, colomboNight, null, null, null
        );
        when(eventRepository.findById("event-qh")).thenReturn(Optional.of(event));

        Article article = mock(Article.class);
        when(article.id()).thenReturn("art1");
        when(article.sourceId()).thenReturn("src1");
        lk.srilankannews.article.ArticleAiEnrichment enrichment = mock(lk.srilankannews.article.ArticleAiEnrichment.class);
        when(enrichment.topics()).thenReturn(List.of(topicId));
        when(article.aiEnrichment()).thenReturn(enrichment);
        when(articleService.findById("art1")).thenReturn(Optional.of(article));

        Story story = mock(Story.class);
        when(story.id()).thenReturn("st1");
        when(storyRepository.findById("st1")).thenReturn(Optional.of(story));

        UserFollow ufTopic = new UserFollow(null, userId, lk.srilankannews.user.FollowTargetType.TOPIC, topicId, null, null);
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(eq(FollowTargetType.TOPIC), argThat(c -> c != null && c.contains(topicId)))).thenReturn(List.of(ufTopic));

        // 22:00 -> 07:00 Asia/Colombo
        NotificationPreference prefs = new NotificationPreference(
                userId, true, "user@example.com", true, false, true, false,
                true, java.time.LocalTime.of(22, 0), java.time.LocalTime.of(7, 0), "Asia/Colombo",
                colomboNight, colomboNight
        );
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(prefs));

        qhService.processEvent(Map.of("eventId", "event-qh", "articleId", "art1", "storyId", "st1", "eventVersion", "v1"));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());

        Notification captured = notificationCaptor.getValue();
        // In-app notification is present immediately
        assertThat(captured.userId()).isEqualTo(userId);
        assertThat(captured.readAt()).isNull();

        // Email delivery is deferred to 07:00 Asia/Colombo (01:30:00 UTC)
        assertThat(captured.emailDelivery().status()).isEqualTo(Notification.EmailDelivery.DeliveryStatus.DEFERRED);
        assertThat(captured.emailDelivery().nextAttemptAt()).isEqualTo(Instant.parse("2026-09-05T01:30:00Z"));
    }

    @Test
    void shouldSetEmailDeliveryToNotRequestedWhenEmailDisabled() {
        String userId = "user-no-email";
        String topicId = "cricket";
        NotificationEvent event = new NotificationEvent(
                "event-no-email", "art1", "st1", "src1", NOW, "v1",
                NotificationEvent.EventStatus.PENDING, 0, NOW, NOW, null, null, null
        );
        when(eventRepository.findById("event-no-email")).thenReturn(Optional.of(event));

        Article article = mock(Article.class);
        when(article.id()).thenReturn("art1");
        when(article.sourceId()).thenReturn("src1");
        lk.srilankannews.article.ArticleAiEnrichment enrichment = mock(lk.srilankannews.article.ArticleAiEnrichment.class);
        when(enrichment.topics()).thenReturn(List.of(topicId));
        when(article.aiEnrichment()).thenReturn(enrichment);
        when(articleService.findById("art1")).thenReturn(Optional.of(article));

        Story story = mock(Story.class);
        when(story.id()).thenReturn("st1");
        when(storyRepository.findById("st1")).thenReturn(Optional.of(story));

        UserFollow ufTopic = new UserFollow(null, userId, lk.srilankannews.user.FollowTargetType.TOPIC, topicId, null, null);
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(eq(FollowTargetType.TOPIC), argThat(c -> c != null && c.contains(topicId)))).thenReturn(List.of(ufTopic));

        // emailEnabled = false
        NotificationPreference prefs = new NotificationPreference(
                userId, true, "user@example.com", false, false, true, false,
                false, null, null, "UTC", NOW, NOW
        );
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(prefs));

        service.processEvent(Map.of("eventId", "event-no-email", "articleId", "art1", "storyId", "st1", "eventVersion", "v1"));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());

        Notification captured = notificationCaptor.getValue();
        assertThat(captured.emailDelivery().status()).isEqualTo(Notification.EmailDelivery.DeliveryStatus.NOT_REQUESTED);
    }

    @Test
    void shouldMatchUserFollowWhenAiTopicsHaveMixedCase() {
        String userId = "user-cricket";
        String sourceId = "ada-derana";
        String followedTopicKey = "sri lanka cricket";

        NotificationEvent event = new NotificationEvent(
                "event-cricket", "article-cricket", "story-cricket", sourceId, NOW, "v1",
                NotificationEvent.EventStatus.PENDING, 0, NOW, NOW, null, null, null
        );
        when(eventRepository.findById("event-cricket")).thenReturn(Optional.of(event));

        Article article = mock(Article.class);
        when(article.id()).thenReturn("article-cricket");
        when(article.sourceId()).thenReturn(sourceId);
        when(article.title()).thenReturn("Sri Lanka Cricket Victory");
        lk.srilankannews.article.ArticleAiEnrichment enrichment = mock(lk.srilankannews.article.ArticleAiEnrichment.class);
        // AI model outputs mixed case with capital 'S' and 'L'
        when(enrichment.topics()).thenReturn(List.of("Cricket", "Sri Lanka cricket"));
        when(enrichment.summary()).thenReturn("Cricket summary");
        when(article.aiEnrichment()).thenReturn(enrichment);
        when(articleService.findById("article-cricket")).thenReturn(Optional.of(article));

        Story story = mock(Story.class);
        when(story.id()).thenReturn("story-cricket");
        when(storyRepository.findById("story-cricket")).thenReturn(Optional.of(story));

        // User follow is stored with normalized lowercase key
        UserFollow uf = new UserFollow(null, userId, FollowTargetType.TOPIC, followedTopicKey, "Sri Lanka Cricket", NOW);
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(eq(FollowTargetType.TOPIC), argThat(c -> c != null && c.contains(followedTopicKey))))
                .thenReturn(List.of(uf));

        NotificationPreference prefs = new NotificationPreference(
                userId, true, "cricket@example.com", true, false, true, false,
                false, null, null, "UTC", NOW, NOW
        );
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(prefs));

        service.processEvent(Map.of("eventId", "event-cricket", "articleId", "article-cricket", "storyId", "story-cricket", "eventVersion", "v1"));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(notificationCaptor.capture());

        Notification captured = notificationCaptor.getValue();
        assertThat(captured.userId()).isEqualTo(userId);
        assertThat(captured.reasons()).contains(Notification.NotificationReason.FOLLOWED_TOPIC);
    }

    @Test
    void shouldMatchUserFollowWhenAiTopicsAlreadyLowercase() {
        String userId = "user-lower";
        String sourceId = "src-1";
        String topicKey = "politics";

        NotificationEvent event = new NotificationEvent(
                "event-lower", "art-lower", "st-lower", sourceId, NOW, "v1",
                NotificationEvent.EventStatus.PENDING, 0, NOW, NOW, null, null, null
        );
        when(eventRepository.findById("event-lower")).thenReturn(Optional.of(event));

        Article article = mock(Article.class);
        when(article.id()).thenReturn("art-lower");
        when(article.sourceId()).thenReturn(sourceId);
        lk.srilankannews.article.ArticleAiEnrichment enrichment = mock(lk.srilankannews.article.ArticleAiEnrichment.class);
        when(enrichment.topics()).thenReturn(List.of("politics"));
        when(article.aiEnrichment()).thenReturn(enrichment);
        when(articleService.findById("art-lower")).thenReturn(Optional.of(article));

        Story story = mock(Story.class);
        when(story.id()).thenReturn("st-lower");
        when(storyRepository.findById("st-lower")).thenReturn(Optional.of(story));

        UserFollow uf = new UserFollow(null, userId, FollowTargetType.TOPIC, topicKey, "Politics", NOW);
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(eq(FollowTargetType.TOPIC), argThat(c -> c != null && c.contains(topicKey))))
                .thenReturn(List.of(uf));

        NotificationPreference prefs = new NotificationPreference(
                userId, true, "lower@example.com", true, false, true, false,
                false, null, null, "UTC", NOW, NOW
        );
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(prefs));

        service.processEvent(Map.of("eventId", "event-lower", "articleId", "art-lower", "storyId", "st-lower", "eventVersion", "v1"));

        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void shouldDeduplicateAiTopicsWithDifferentCasing() {
        String userId = "user-dedupe";
        String sourceId = "src-1";

        NotificationEvent event = new NotificationEvent(
                "event-case", "art-case", "st-case", sourceId, NOW, "v1",
                NotificationEvent.EventStatus.PENDING, 0, NOW, NOW, null, null, null
        );
        when(eventRepository.findById("event-case")).thenReturn(Optional.of(event));

        Article article = mock(Article.class);
        when(article.id()).thenReturn("art-case");
        when(article.sourceId()).thenReturn(sourceId);
        lk.srilankannews.article.ArticleAiEnrichment enrichment = mock(lk.srilankannews.article.ArticleAiEnrichment.class);
        // Duplicate topics with different casing
        when(enrichment.topics()).thenReturn(List.of("Cricket", "cricket", "CRICKET"));
        when(article.aiEnrichment()).thenReturn(enrichment);
        when(articleService.findById("art-case")).thenReturn(Optional.of(article));

        Story story = mock(Story.class);
        when(story.id()).thenReturn("st-case");
        when(storyRepository.findById("st-case")).thenReturn(Optional.of(story));

        UserFollow uf = new UserFollow(null, userId, FollowTargetType.TOPIC, "cricket", "Cricket", NOW);
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(eq(FollowTargetType.TOPIC), argThat(c -> c != null && c.size() == 1 && c.contains("cricket"))))
                .thenReturn(List.of(uf));

        NotificationPreference prefs = new NotificationPreference(
                userId, true, "dedupe@example.com", true, false, true, false,
                false, null, null, "UTC", NOW, NOW
        );
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(prefs));

        service.processEvent(Map.of("eventId", "event-case", "articleId", "art-case", "storyId", "st-case", "eventVersion", "v1"));

        // Only queried with the distinct set size 1, and created exactly 1 notification
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void shouldSafelyHandleNullAndBlankAiTopics() {
        String sourceId = "src-1";

        NotificationEvent event = new NotificationEvent(
                "event-nulls", "art-nulls", "st-nulls", sourceId, NOW, "v1",
                NotificationEvent.EventStatus.PENDING, 0, NOW, NOW, null, null, null
        );
        when(eventRepository.findById("event-nulls")).thenReturn(Optional.of(event));

        Article article = mock(Article.class);
        when(article.id()).thenReturn("art-nulls");
        when(article.sourceId()).thenReturn(sourceId);
        lk.srilankannews.article.ArticleAiEnrichment enrichment = mock(lk.srilankannews.article.ArticleAiEnrichment.class);
        java.util.ArrayList<String> nullAndBlankList = new java.util.ArrayList<>();
        nullAndBlankList.add(null);
        nullAndBlankList.add("");
        nullAndBlankList.add("   ");
        when(enrichment.topics()).thenReturn(nullAndBlankList);
        when(article.aiEnrichment()).thenReturn(enrichment);
        when(articleService.findById("art-nulls")).thenReturn(Optional.of(article));

        Story story = mock(Story.class);
        when(story.id()).thenReturn("st-nulls");
        when(storyRepository.findById("st-nulls")).thenReturn(Optional.of(story));

        service.processEvent(Map.of("eventId", "event-nulls", "articleId", "art-nulls", "storyId", "st-nulls", "eventVersion", "v1"));

        // Repository should not be queried with empty topics
        verify(followRepository, never()).findUserIdsByTargetTypeAndTargetKeyIn(any(), any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldNotCreateNotificationWhenNoTopicsMatch() {
        String sourceId = "src-1";

        NotificationEvent event = new NotificationEvent(
                "event-nomatch", "art-nomatch", "st-nomatch", sourceId, NOW, "v1",
                NotificationEvent.EventStatus.PENDING, 0, NOW, NOW, null, null, null
        );
        when(eventRepository.findById("event-nomatch")).thenReturn(Optional.of(event));

        Article article = mock(Article.class);
        when(article.id()).thenReturn("art-nomatch");
        when(article.sourceId()).thenReturn(sourceId);
        lk.srilankannews.article.ArticleAiEnrichment enrichment = mock(lk.srilankannews.article.ArticleAiEnrichment.class);
        when(enrichment.topics()).thenReturn(List.of("Swimming"));
        when(article.aiEnrichment()).thenReturn(enrichment);
        when(articleService.findById("art-nomatch")).thenReturn(Optional.of(article));

        Story story = mock(Story.class);
        when(story.id()).thenReturn("st-nomatch");
        when(storyRepository.findById("st-nomatch")).thenReturn(Optional.of(story));

        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(eq(FollowTargetType.TOPIC), any()))
                .thenReturn(List.of());

        service.processEvent(Map.of("eventId", "event-nomatch", "articleId", "art-nomatch", "storyId", "st-nomatch", "eventVersion", "v1"));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldNotCreateDuplicateNotificationWhenEventIsReplayed() {
        String userId = "user-dup";
        String sourceId = "src-1";
        String topicKey = "cricket";

        NotificationEvent event = new NotificationEvent(
                "event-dup", "art-dup", "st-dup", sourceId, NOW, "v1",
                NotificationEvent.EventStatus.PROCESSED, 0, NOW, NOW, null, null, null
        );
        when(eventRepository.findById("event-dup")).thenReturn(Optional.of(event));

        Article article = mock(Article.class);
        when(article.id()).thenReturn("art-dup");
        when(article.sourceId()).thenReturn(sourceId);
        lk.srilankannews.article.ArticleAiEnrichment enrichment = mock(lk.srilankannews.article.ArticleAiEnrichment.class);
        when(enrichment.topics()).thenReturn(List.of("Cricket"));
        when(article.aiEnrichment()).thenReturn(enrichment);
        when(articleService.findById("art-dup")).thenReturn(Optional.of(article));

        Story story = mock(Story.class);
        when(story.id()).thenReturn("st-dup");
        when(storyRepository.findById("st-dup")).thenReturn(Optional.of(story));

        UserFollow uf = new UserFollow(null, userId, FollowTargetType.TOPIC, topicKey, "Cricket", NOW);
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(eq(FollowTargetType.TOPIC), argThat(c -> c != null && c.contains(topicKey))))
                .thenReturn(List.of(uf));

        NotificationPreference prefs = new NotificationPreference(
                userId, true, "dup@example.com", true, false, true, false,
                false, null, null, "UTC", NOW, NOW
        );
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(prefs));

        // Simulate duplicate key on save
        doThrow(new org.springframework.dao.DuplicateKeyException("Duplicate dedupeKey"))
                .when(notificationRepository).save(any(Notification.class));

        // Replaying does not throw exception
        service.processEvent(Map.of("eventId", "event-dup", "articleId", "art-dup", "storyId", "st-dup", "eventVersion", "v1"));

        verify(notificationRepository, times(1)).save(any());
    }
}
