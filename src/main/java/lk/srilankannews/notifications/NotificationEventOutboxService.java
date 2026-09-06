package lk.srilankannews.notifications;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationEventOutboxService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventOutboxService.class);
    private final NotificationEventRepository repository;
    private final NotificationEventPublisher publisher;
    private final Clock clock;

    public NotificationEventOutboxService(NotificationEventRepository repository,
                                          NotificationEventPublisher publisher,
                                          Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    public void dispatch(String articleId, String storyId, String sourceId, String eventVersion) {
        if (repository.existsByArticleIdAndEventVersion(articleId, eventVersion)) {
            LOGGER.debug("Notification event already exists for article {} version {}", articleId, eventVersion);
            return;
        }

        NotificationEvent event = new NotificationEvent(
                null,
                articleId,
                storyId,
                sourceId,
                clock.instant(),
                eventVersion,
                NotificationEvent.EventStatus.PENDING,
                0,
                clock.instant(), // nextAttemptAt
                null,
                null,
                null,
                null // expiresAt (null for active PENDING status)
        );

        event = repository.save(event);
        publisher.publish(event);
    }
}
