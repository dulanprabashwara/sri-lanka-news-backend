package lk.srilankannews.notifications;

import lk.srilankannews.retention.RetentionPolicyService;
import lk.srilankannews.retention.RetentionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class NotificationControllerTest {

    private NotificationRepository notificationRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private UnsubscribeTokenService unsubscribeTokenService;
    private EmailNotificationProvider emailProvider;
    private RetentionPolicyService retentionPolicyService;
    private Clock clock;
    private lk.srilankannews.analytics.AnalyticsRecorder analyticsRecorder;
    private NotificationController controller;

    private final Instant NOW = Instant.parse("2026-09-04T10:00:00Z");

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        unsubscribeTokenService = mock(UnsubscribeTokenService.class);
        emailProvider = mock(EmailNotificationProvider.class);
        retentionPolicyService = new RetentionPolicyService(RetentionProperties.defaults());
        clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        analyticsRecorder = mock(lk.srilankannews.analytics.AnalyticsRecorder.class);
        
        controller = new NotificationController(
                notificationRepository, preferenceRepository, unsubscribeTokenService, emailProvider, retentionPolicyService, clock, analyticsRecorder
        );
    }

    @Test
    void markRead_calculatesReadExpiryAndSetsReadAt() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("user-123");

        Notification notification = new Notification(
                "n1", "user-123", Notification.NotificationType.STORY_ACTIVITY, "st1", "art1",
                "src1", "slug1", "Source", List.of(), "Title", "Msg", "/path", "v1", "dedupe1",
                NOW.minus(Duration.ofDays(1)), null, null, NOW.plus(Duration.ofDays(364))
        );

        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));

        controller.markRead("n1", authentication);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification updated = captor.getValue();
        assertThat(updated.readAt()).isEqualTo(NOW);
        assertThat(updated.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(180)));
    }

    @Test
    void markAllRead_invokesRepositoryWithReadAtAndExpiresAt() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("user-123");

        controller.markAllRead(authentication);

        verify(notificationRepository).markAllReadForUser("user-123", NOW, NOW.plus(Duration.ofDays(180)));
    }

    @Test
    void shouldExtractEmailFromJwtAndIgnoreRequestPayload() {
        // Arrange
        NotificationPreferenceRequest request = new NotificationPreferenceRequest(
                true, true, true, true, true, false, null, null, "UTC"
        );
        
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("user-123");
        
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("email")).thenReturn("trusted@example.com");
        when(authentication.getPrincipal()).thenReturn(jwt);
        
        when(preferenceRepository.findById("user-123")).thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        NotificationPreferenceResponse updated = controller.updatePreferences(request, authentication);

        // Assert
        verify(preferenceRepository).save(argThat(p -> "trusted@example.com".equals(p.email())));
    }
}
