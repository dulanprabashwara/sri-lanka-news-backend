package lk.srilankannews.notifications;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import lk.srilankannews.analytics.AnalyticsEventType;
import lk.srilankannews.analytics.AnalyticsRecorder;
import lk.srilankannews.retention.RetentionPolicyService;
import lk.srilankannews.common.domain.Language;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UnsubscribeTokenService unsubscribeTokenService;
    private final EmailNotificationProvider emailProvider;
    private final RetentionPolicyService retentionPolicyService;
    private final Clock clock;
    private final AnalyticsRecorder analyticsRecorder;
    private final NotificationLocalizationService localizationService;

    @Autowired
    public NotificationController(NotificationRepository notificationRepository,
                                  NotificationPreferenceRepository preferenceRepository,
                                  UnsubscribeTokenService unsubscribeTokenService,
                                  EmailNotificationProvider emailProvider,
                                  RetentionPolicyService retentionPolicyService,
                                  Clock clock,
                                  AnalyticsRecorder analyticsRecorder,
                                  NotificationLocalizationService localizationService) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.unsubscribeTokenService = unsubscribeTokenService;
        this.emailProvider = emailProvider;
        this.retentionPolicyService = retentionPolicyService;
        this.clock = clock;
        this.analyticsRecorder = analyticsRecorder;
        this.localizationService = localizationService;
    }

    public NotificationController(NotificationRepository notificationRepository,
                                  NotificationPreferenceRepository preferenceRepository,
                                  UnsubscribeTokenService unsubscribeTokenService,
                                  EmailNotificationProvider emailProvider,
                                  RetentionPolicyService retentionPolicyService,
                                  Clock clock,
                                  AnalyticsRecorder analyticsRecorder) {
        this(notificationRepository, preferenceRepository, unsubscribeTokenService, emailProvider,
                retentionPolicyService, clock, analyticsRecorder, null);
    }

    @GetMapping("/me/notifications")
    @PreAuthorize("isAuthenticated()")
    public Page<NotificationResponse> getNotifications(
            Authentication authentication,
            @RequestParam(required = false) Language displayLanguage,
            @RequestParam(required = false) Language language,
            Pageable pageable) {
        Language requestedLanguage = displayLanguage != null ? displayLanguage : language;
        if (localizationService != null) {
            return localizationService.getLocalizedNotifications(authentication.getName(), requestedLanguage, pageable);
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(authentication.getName(), pageable)
                .map(NotificationResponse::from);
    }

    @GetMapping("/me/notifications/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> getUnreadCount(Authentication authentication) {
        long count = notificationRepository.countUnreadByUserId(authentication.getName());
        return Map.of("count", count);
    }

    @PostMapping("/me/notifications/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(@PathVariable String id, Authentication authentication) {
        notificationRepository.findById(id).ifPresent(notification -> {
            if (notification.userId().equals(authentication.getName()) && notification.readAt() == null) {
                Instant readAt = clock.instant();
                Instant expiresAt = retentionPolicyService.calculateNotificationReadExpiry(readAt).orElse(null);

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
                        readAt, // set readAt
                        notification.emailDelivery(),
                        expiresAt
                );
                notificationRepository.save(updated);
                
                analyticsRecorder.recordBestEffort(AnalyticsEventType.NOTIFICATION_READ, notification.id(), 
                        notification.triggeringArticleId(), notification.storyId(), notification.sourceId(), null, null, null);
            }
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/me/notifications/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        Instant readAt = clock.instant();
        Instant expiresAt = retentionPolicyService.calculateNotificationReadExpiry(readAt).orElse(null);
        notificationRepository.markAllReadForUser(authentication.getName(), readAt, expiresAt);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me/notification-preferences")
    @PreAuthorize("isAuthenticated()")
    public NotificationPreferenceResponse getPreferences(Authentication authentication) {
        NotificationPreference prefs = preferenceRepository.findById(authentication.getName())
                .orElseGet(() -> NotificationPreference.defaultPreferences(authentication.getName(), clock.instant()));
        return new NotificationPreferenceResponse(prefs, emailProvider.isAvailable());
    }

    @PutMapping("/me/notification-preferences")
    @PreAuthorize("isAuthenticated()")
    public NotificationPreferenceResponse updatePreferences(@RequestBody NotificationPreferenceRequest request, Authentication authentication) {
        String email = null;
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            email = jwt.getClaimAsString("email");
        }
        
        NotificationPreference existing = preferenceRepository.findById(authentication.getName())
            .orElseGet(() -> NotificationPreference.defaultPreferences(authentication.getName(), clock.instant()));

        NotificationPreference prefs = new NotificationPreference(
                authentication.getName(),
                request.inAppEnabled(),
                email,
                request.emailEnabled(),
                request.sourceFollowNotificationsEnabled(),
                request.topicFollowNotificationsEnabled(),
                request.storyUpdateNotificationsEnabled(),
                request.quietHoursEnabled(),
                request.quietHoursStart() != null ? java.time.LocalTime.parse(request.quietHoursStart()) : null,
                request.quietHoursEnd() != null ? java.time.LocalTime.parse(request.quietHoursEnd()) : null,
                request.timezone(),
                existing.createdAt(),
                clock.instant()
        );
        NotificationPreference saved = preferenceRepository.save(prefs);
        return new NotificationPreferenceResponse(saved, emailProvider.isAvailable());
    }

    @PostMapping("/notifications/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String userId = unsubscribeTokenService.validateTokenAndGetUserId(token);
            preferenceRepository.findById(userId).ifPresent(prefs -> {
                NotificationPreference updated = new NotificationPreference(
                        prefs.userId(),
                        prefs.inAppEnabled(),
                        prefs.email(),
                        false, // disable email
                        prefs.sourceFollowNotificationsEnabled(),
                        prefs.topicFollowNotificationsEnabled(),
                        prefs.storyUpdateNotificationsEnabled(),
                        prefs.quietHoursEnabled(),
                        prefs.quietHoursStart(),
                        prefs.quietHoursEnd(),
                        prefs.timezone(),
                        prefs.createdAt(),
                        clock.instant()
                );
                preferenceRepository.save(updated);
            });
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
