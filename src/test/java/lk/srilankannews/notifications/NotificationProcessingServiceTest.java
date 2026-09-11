package lk.srilankannews.notifications;

import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.retention.RetentionPolicyService;
import lk.srilankannews.retention.RetentionProperties;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(lk.srilankannews.user.FollowTargetType.TOPIC, List.of(topicId))).thenReturn(List.of(ufTopic));
        
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
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(lk.srilankannews.user.FollowTargetType.TOPIC, List.of(topicId))).thenReturn(List.of(ufTopic));
        
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
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(lk.srilankannews.user.FollowTargetType.TOPIC, List.of(topicId))).thenReturn(List.of(ufTopic));

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
        when(followRepository.findUserIdsByTargetTypeAndTargetKeyIn(lk.srilankannews.user.FollowTargetType.TOPIC, List.of(topicId))).thenReturn(List.of(ufTopic));

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
}
