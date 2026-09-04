package lk.srilankannews.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class NotificationControllerTest {

    private NotificationRepository notificationRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private UnsubscribeTokenService unsubscribeTokenService;
    private EmailNotificationProvider emailProvider;
    private Clock clock;
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        unsubscribeTokenService = mock(UnsubscribeTokenService.class);
        emailProvider = mock(EmailNotificationProvider.class);
        clock = Clock.fixed(Instant.parse("2026-09-04T10:00:00Z"), ZoneId.of("UTC"));
        
        controller = new NotificationController(
                notificationRepository, preferenceRepository, unsubscribeTokenService, emailProvider, clock
        );
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
