package lk.srilankannews.notifications;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import lk.srilankannews.user.FollowTargetType;
import lk.srilankannews.user.UserFollowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class NotificationProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationProcessingService.class);

    private final ArticleService articleService;
    private final StoryRepository storyRepository;
    private final SourceService sourceService;
    private final UserFollowRepository followRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationEventRepository eventRepository;
    private final Clock clock;

    public NotificationProcessingService(ArticleService articleService,
                                         StoryRepository storyRepository,
                                         SourceService sourceService,
                                         UserFollowRepository followRepository,
                                         NotificationPreferenceRepository preferenceRepository,
                                         NotificationRepository notificationRepository,
                                         NotificationEventRepository eventRepository,
                                         Clock clock) {
        this.articleService = articleService;
        this.storyRepository = storyRepository;
        this.sourceService = sourceService;
        this.followRepository = followRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationRepository = notificationRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    public void processEvent(Map<String, String> payload) {
        String eventId = payload.get("eventId");
        String articleId = payload.get("articleId");
        String storyId = payload.get("storyId");
        String eventVersion = payload.get("eventVersion");
        int attempt = Integer.parseInt(payload.getOrDefault("attempt", "0"));

        Optional<NotificationEvent> optionalEvent = eventRepository.findById(eventId);
        if (optionalEvent.isEmpty()) {
            LOGGER.warn("Notification event {} not found in db", eventId);
            return;
        }

        NotificationEvent event = optionalEvent.get();
        if (event.status() == NotificationEvent.EventStatus.PROCESSED) {
            LOGGER.debug("Event {} already processed", eventId);
            return;
        }

        try {
            Article article = articleService.findById(articleId).orElse(null);
            Story story = storyRepository.findById(storyId).orElse(null);
            
            if (article == null || story == null) {
                LOGGER.warn("Article or Story not found for event {}", eventId);
                markEvent(event, NotificationEvent.EventStatus.FAILED, "Article or Story not found");
                return;
            }

            Source source = sourceService.findById(article.sourceId()).orElse(null);
            String sourceSlug = source != null ? source.slug() : "unknown";
            String sourceName = source != null ? source.name() : "Unknown Publisher";

            Map<String, Set<Notification.NotificationReason>> userReasons = new HashMap<>();

            // Find source followers
            followRepository.findUserIdsByTargetTypeAndTargetKey(FollowTargetType.SOURCE, article.sourceId())
                    .forEach(f -> userReasons.computeIfAbsent(f.userId(), k -> new HashSet<>()).add(Notification.NotificationReason.FOLLOWED_SOURCE));

            // Find topic followers
            if (article.aiEnrichment() != null && article.aiEnrichment().topics() != null) {
                followRepository.findUserIdsByTargetTypeAndTargetKeyIn(FollowTargetType.TOPIC, article.aiEnrichment().topics())
                        .forEach(f -> userReasons.computeIfAbsent(f.userId(), k -> new HashSet<>()).add(Notification.NotificationReason.FOLLOWED_TOPIC));
            }

            for (Map.Entry<String, Set<Notification.NotificationReason>> entry : userReasons.entrySet()) {
                processUserNotification(entry.getKey(), entry.getValue(), article, story, sourceSlug, sourceName, eventVersion);
            }

            markEvent(event, NotificationEvent.EventStatus.PROCESSED, null);
        } catch (Exception e) {
            LOGGER.error("Error processing event {}", eventId, e);
            markEvent(event, attempt > 3 ? NotificationEvent.EventStatus.FAILED : NotificationEvent.EventStatus.RETRYING, e.getMessage());
            throw e;
        }
    }

    private void processUserNotification(String userId, Set<Notification.NotificationReason> reasons, Article article, Story story, String sourceSlug, String sourceName, String eventVersion) {
        NotificationPreference prefs = preferenceRepository.findById(userId)
                .orElseGet(() -> NotificationPreference.defaultPreferences(userId, clock.instant()));

        if (!prefs.inAppEnabled() && !prefs.emailEnabled()) {
            return; // completely disabled
        }

        // Check if reasons align with preferences
        boolean reasonMatches = false;
        if (prefs.sourceFollowNotificationsEnabled() && reasons.contains(Notification.NotificationReason.FOLLOWED_SOURCE)) {
            reasonMatches = true;
        }
        if (prefs.topicFollowNotificationsEnabled() && reasons.contains(Notification.NotificationReason.FOLLOWED_TOPIC)) {
            reasonMatches = true;
        }

        if (!reasonMatches) {
            return; // Neither matched active preference
        }

        String dedupeKey = "dedupe_v1:" + userId + ":" + story.id() + ":" + Notification.NotificationType.STORY_ACTIVITY.name() + ":" + eventVersion;
        
        Notification.EmailDelivery emailDelivery = buildEmailDelivery(prefs);

        String title = story.canonicalTitle() != null ? story.canonicalTitle() : article.title();
        String message = article.aiEnrichment() != null ? article.aiEnrichment().summary() : "";

        Notification notification = new Notification(
                null,
                userId,
                Notification.NotificationType.STORY_ACTIVITY,
                story.id(),
                article.id(),
                article.sourceId(),
                sourceSlug,
                sourceName,
                new ArrayList<>(reasons),
                title,
                message,
                "/story/" + story.id(),
                eventVersion,
                dedupeKey,
                clock.instant(),
                null, // readAt
                emailDelivery
        );

        try {
            notificationRepository.save(notification);
        } catch (DuplicateKeyException e) {
            LOGGER.debug("Duplicate notification skipped for user {} eventVersion {}", userId, eventVersion);
        }
    }

    private Notification.EmailDelivery buildEmailDelivery(NotificationPreference prefs) {
        if (!prefs.emailEnabled()) {
            return new Notification.EmailDelivery(Notification.EmailDelivery.DeliveryStatus.NOT_REQUESTED, 0, null, null, null);
        }

        Instant now = clock.instant();
        if (prefs.quietHoursEnabled() && prefs.quietHoursStart() != null && prefs.quietHoursEnd() != null && prefs.timezone() != null) {
            try {
                ZoneId zoneId = ZoneId.of(prefs.timezone());
                ZonedDateTime zdt = now.atZone(zoneId);
                LocalTime localTime = zdt.toLocalTime();
                
                boolean inQuietHours = false;
                if (prefs.quietHoursStart().isBefore(prefs.quietHoursEnd())) {
                    inQuietHours = !localTime.isBefore(prefs.quietHoursStart()) && localTime.isBefore(prefs.quietHoursEnd());
                } else {
                    inQuietHours = !localTime.isBefore(prefs.quietHoursStart()) || localTime.isBefore(prefs.quietHoursEnd());
                }

                if (inQuietHours) {
                    ZonedDateTime nextAttempt = zdt.with(prefs.quietHoursEnd());
                    if (!nextAttempt.isAfter(zdt)) {
                        nextAttempt = nextAttempt.plusDays(1);
                    }
                    return new Notification.EmailDelivery(Notification.EmailDelivery.DeliveryStatus.DEFERRED, 0, nextAttempt.toInstant(), null, null);
                }
            } catch (Exception e) {
                LOGGER.warn("Quiet hours parsing error for user {}", prefs.userId(), e);
            }
        }

        return new Notification.EmailDelivery(Notification.EmailDelivery.DeliveryStatus.PENDING, 0, now, null, null);
    }

    private void markEvent(NotificationEvent event, NotificationEvent.EventStatus status, String error) {
        NotificationEvent updated = new NotificationEvent(
                event.id(),
                event.articleId(),
                event.storyId(),
                event.sourceId(),
                event.occurredAt(),
                event.eventVersion(),
                status,
                event.attemptCount(),
                null,
                event.publishedAt(),
                status == NotificationEvent.EventStatus.PROCESSED ? clock.instant() : event.processedAt(),
                error
        );
        eventRepository.save(updated);
    }
}
