package lk.srilankannews.analytics;

import lk.srilankannews.user.UserPreferencesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
@Service
public class AnalyticsEventService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final AnalyticsEventRepository eventRepository;
    private final AnalyticsDailyVisitorRepository visitorRepository;
    private final AnalyticsPseudonymService pseudonymService;
    private final UserPreferencesService preferencesService;
    private final Clock clock;
    private final boolean enabled;
    private final int metadataVersion = 1;

    public AnalyticsEventService(
            AnalyticsEventRepository eventRepository,
            AnalyticsDailyVisitorRepository visitorRepository,
            AnalyticsPseudonymService pseudonymService,
            UserPreferencesService preferencesService,
            Clock clock,
            @Value("${analytics.enabled:true}") boolean enabled) {
        this.eventRepository = eventRepository;
        this.visitorRepository = visitorRepository;
        this.pseudonymService = pseudonymService;
        this.preferencesService = preferencesService;
        this.clock = clock;
        this.enabled = enabled;
    }

    public void recordClientBatch(String userId, AnalyticsEventBatchRequest request) {
        if (!enabled) return;

        VisitorType visitorType = userId != null ? VisitorType.AUTHENTICATED : VisitorType.GUEST;
        String rawSubject = userId != null ? userId : request.sessionId();
        String visitorKey = rawSubject != null ? pseudonymService.generateVisitorKey(rawSubject) : null;

        // Honor opt-out for authenticated users
        if (userId != null) {
            Boolean analyticsEnabled = preferencesService.get(userId).analyticsEnabled();
            if (Boolean.FALSE.equals(analyticsEnabled)) {
                return;
            }
        }

        if (visitorKey != null) {
            recordUniqueVisitor(visitorKey, visitorType);
        }

        Instant receivedAt = clock.instant();

        for (AnalyticsEventDto dto : request.events()) {
            Instant occurred = dto.occurredAt() != null ? Instant.ofEpochMilli(dto.occurredAt()) : receivedAt;
            // Bound client timestamp to within 1 hour of server time
            if (occurred.isBefore(receivedAt.minusSeconds(3600)) || occurred.isAfter(receivedAt.plusSeconds(3600))) {
                occurred = receivedAt;
            }

            AnalyticsEvent event = new AnalyticsEvent(
                    null,
                    dto.eventId(),
                    dto.eventType(),
                    occurred,
                    receivedAt,
                    visitorKey,
                    visitorType,
                    request.routeType(),
                    dto.articleId(),
                    dto.storyId(),
                    dto.sourceId(),
                    dto.category(),
                    dto.language(),
                    dto.searchMode(),
                    null, // resultCount only for server-side search
                    dto.position(),
                    metadataVersion
            );

            try {
                eventRepository.save(event);
            } catch (DuplicateKeyException e) {
                // Idempotent success
            } catch (Exception e) {
                log.warn("Failed to save analytics event: {}", e.getMessage());
            }
        }
    }

    public void recordServerEvent(AnalyticsEventType type, String eventId, String articleId, String storyId, String sourceId, Integer resultCount, String searchMode, String category) {
        if (!enabled) return;
        Instant now = clock.instant();
        AnalyticsEvent event = new AnalyticsEvent(
                null,
                eventId != null ? eventId : java.util.UUID.randomUUID().toString(),
                type,
                now,
                now,
                null, // No visitor key for server aggregate events
                null,
                null,
                articleId,
                storyId,
                sourceId,
                category,
                null,
                searchMode,
                resultCount,
                null,
                metadataVersion
        );
        try {
            eventRepository.save(event);
        } catch (DuplicateKeyException e) {
            // Idempotent
        } catch (Exception e) {
            log.warn("Failed to record server event", e);
        }
    }

    private void recordUniqueVisitor(String visitorKey, VisitorType type) {
        String date = DATE_FORMATTER.format(clock.instant());
        try {
            visitorRepository.save(new AnalyticsDailyVisitor(null, date, visitorKey, type));
        } catch (DuplicateKeyException e) {
            // Already recorded today
        } catch (Exception e) {
            log.warn("Failed to record unique visitor", e);
        }
    }
}
