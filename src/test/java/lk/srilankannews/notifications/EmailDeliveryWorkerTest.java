package lk.srilankannews.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EmailDeliveryWorkerTest {

    private NotificationRepository notificationRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private EmailNotificationProvider emailProvider;
    private UnsubscribeTokenService unsubscribeTokenService;
    private Clock clock;
    private EmailDeliveryWorker worker;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        emailProvider = mock(EmailNotificationProvider.class);
        unsubscribeTokenService = mock(UnsubscribeTokenService.class);
        clock = Clock.fixed(Instant.parse("2026-09-04T10:00:00Z"), ZoneId.of("UTC"));
        worker = new EmailDeliveryWorker(notificationRepository, emailProvider, preferenceRepository, unsubscribeTokenService, clock, "http://localhost:3000");
    }

    @Test
    void shouldSendEmailAndMarkAsSent() throws Exception {
        Notification notification = mock(Notification.class);
        when(notification.userId()).thenReturn("user1");
        when(notification.id()).thenReturn("n1");
        when(notification.emailDelivery()).thenReturn(new Notification.EmailDelivery(Notification.EmailDelivery.DeliveryStatus.PENDING, 0, null, null, null));
        
        when(notificationRepository.findEmailsToDelivery(any(), any())).thenReturn(List.of(notification));
        when(emailProvider.isAvailable()).thenReturn(true);
        when(unsubscribeTokenService.generateToken("user1")).thenReturn("token123");
        
        NotificationPreference prefs = mock(NotificationPreference.class);
        when(prefs.email()).thenReturn("user@test.com");
        when(preferenceRepository.findById("user1")).thenReturn(Optional.of(prefs));

        worker.processEmails();

        verify(emailProvider).sendNotification(any(), eq("user@test.com"), anyString());
        
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification updated = captor.getValue();
        assertThat(updated.emailDelivery().status()).isEqualTo(Notification.EmailDelivery.DeliveryStatus.SENT);
    }

    @Test
    void shouldMarkFailedWhenEmailIsMissing() throws Exception {
        Notification notification = mock(Notification.class);
        when(notification.userId()).thenReturn("user1");
        when(notification.id()).thenReturn("n1");
        when(notification.emailDelivery()).thenReturn(new Notification.EmailDelivery(Notification.EmailDelivery.DeliveryStatus.PENDING, 0, null, null, null));
        
        when(notificationRepository.findEmailsToDelivery(any(), any())).thenReturn(List.of(notification));
        when(emailProvider.isAvailable()).thenReturn(true);
        
        NotificationPreference prefs = mock(NotificationPreference.class);
        when(prefs.email()).thenReturn(null);
        when(preferenceRepository.findById("user1")).thenReturn(Optional.of(prefs));

        worker.processEmails();

        verify(emailProvider, never()).sendNotification(any(), any(), any());
        
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification updated = captor.getValue();
        assertThat(updated.emailDelivery().status()).isEqualTo(Notification.EmailDelivery.DeliveryStatus.FAILED);
    }

    @Test
    void shouldScheduleRetryWhenSendFails() throws Exception {
        Notification notification = mock(Notification.class);
        when(notification.userId()).thenReturn("user1");
        when(notification.id()).thenReturn("n1");
        when(notification.emailDelivery()).thenReturn(new Notification.EmailDelivery(Notification.EmailDelivery.DeliveryStatus.PENDING, 0, null, null, null));
        
        when(notificationRepository.findEmailsToDelivery(any(), any())).thenReturn(List.of(notification));
        when(emailProvider.isAvailable()).thenReturn(true);
        when(unsubscribeTokenService.generateToken("user1")).thenReturn("token123");
        
        NotificationPreference prefs = mock(NotificationPreference.class);
        when(prefs.email()).thenReturn("user@test.com");
        when(preferenceRepository.findById("user1")).thenReturn(Optional.of(prefs));
        
        doThrow(new RuntimeException("SMTP error")).when(emailProvider).sendNotification(any(), any(), any());

        worker.processEmails();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification updated = captor.getValue();
        assertThat(updated.emailDelivery().status()).isEqualTo(Notification.EmailDelivery.DeliveryStatus.RETRYING);
        assertThat(updated.emailDelivery().attemptCount()).isEqualTo(1);
        assertThat(updated.emailDelivery().nextAttemptAt()).isNotNull();
    }
}
