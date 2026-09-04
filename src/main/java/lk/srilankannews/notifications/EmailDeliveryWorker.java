package lk.srilankannews.notifications;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailDeliveryWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailDeliveryWorker.class);

    private final NotificationRepository notificationRepository;
    private final EmailNotificationProvider emailProvider;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UnsubscribeTokenService unsubscribeTokenService;
    private final Clock clock;
    private final String publicBaseUrl;

    public EmailDeliveryWorker(NotificationRepository notificationRepository,
                               EmailNotificationProvider emailProvider,
                               NotificationPreferenceRepository preferenceRepository,
                               UnsubscribeTokenService unsubscribeTokenService,
                               Clock clock,
                               @Value("${app.public.base-url:http://localhost:3000}") String publicBaseUrl) {
        this.notificationRepository = notificationRepository;
        this.emailProvider = emailProvider;
        this.preferenceRepository = preferenceRepository;
        this.unsubscribeTokenService = unsubscribeTokenService;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Scheduled(fixedDelay = 30000)
    public void processEmails() {
        if (!emailProvider.isAvailable()) {
            return;
        }

        List<Notification> pending = notificationRepository.findEmailsToDelivery(
                List.of(Notification.EmailDelivery.DeliveryStatus.PENDING, 
                        Notification.EmailDelivery.DeliveryStatus.DEFERRED,
                        Notification.EmailDelivery.DeliveryStatus.RETRYING),
                clock.instant()
        );

        for (Notification notification : pending) {
            processDelivery(notification);
        }
    }

    private void processDelivery(Notification notification) {
        try {
            Optional<NotificationPreference> optionalPref = preferenceRepository.findById(notification.userId());
            if (optionalPref.isEmpty() || optionalPref.get().email() == null) {
                markFailed(notification, "User preference or email not found");
                return;
            }

            String email = optionalPref.get().email();
            String token = unsubscribeTokenService.generateToken(notification.userId());
            String unsubscribeUrl = publicBaseUrl + "/notifications/unsubscribe?token=" + token;

            emailProvider.sendNotification(notification, email, unsubscribeUrl);
            markSent(notification);
        } catch (Exception e) {
            LOGGER.error("Email delivery failed for notification {}", notification.id(), e);
            scheduleRetry(notification, e.getMessage());
        }
    }

    private void markSent(Notification notification) {
        Notification.EmailDelivery updatedDelivery = new Notification.EmailDelivery(
                Notification.EmailDelivery.DeliveryStatus.SENT,
                notification.emailDelivery().attemptCount() + 1,
                null,
                clock.instant(),
                null
        );
        saveWithUpdatedDelivery(notification, updatedDelivery);
    }

    private void markFailed(Notification notification, String error) {
        Notification.EmailDelivery updatedDelivery = new Notification.EmailDelivery(
                Notification.EmailDelivery.DeliveryStatus.FAILED,
                notification.emailDelivery().attemptCount() + 1,
                null,
                null,
                error
        );
        saveWithUpdatedDelivery(notification, updatedDelivery);
    }

    private void scheduleRetry(Notification notification, String error) {
        int attempt = notification.emailDelivery().attemptCount() + 1;
        if (attempt > 5) {
            markFailed(notification, error);
            return;
        }
        Instant nextAttempt = clock.instant().plus(Duration.ofMinutes(2L * attempt));
        Notification.EmailDelivery updatedDelivery = new Notification.EmailDelivery(
                Notification.EmailDelivery.DeliveryStatus.RETRYING,
                attempt,
                nextAttempt,
                null,
                error
        );
        saveWithUpdatedDelivery(notification, updatedDelivery);
    }

    private void saveWithUpdatedDelivery(Notification notification, Notification.EmailDelivery emailDelivery) {
        Notification updated = new Notification(
                notification.id(),
                notification.userId(),
                notification.type(),
                notification.storyId(),
                notification.triggeringArticleId(),
                notification.sourceId(),
                notification.sourceSlug(),
                notification.sourceName(),
                notification.reasons(),
                notification.title(),
                notification.message(),
                notification.linkPath(),
                notification.eventVersion(),
                notification.dedupeKey(),
                notification.createdAt(),
                notification.readAt(),
                emailDelivery
        );
        notificationRepository.save(updated);
    }
}
