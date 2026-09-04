package lk.srilankannews.notifications;

import java.time.Clock;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventRepublisher {

    private final NotificationEventRepository repository;
    private final NotificationEventPublisher publisher;
    private final Clock clock;

    public NotificationEventRepublisher(NotificationEventRepository repository,
                                        NotificationEventPublisher publisher,
                                        Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60000)
    public void republishEvents() {
        List<NotificationEvent> events = repository.findEventsToRetry(
                List.of(NotificationEvent.EventStatus.PENDING, NotificationEvent.EventStatus.RETRYING),
                clock.instant()
        );

        for (NotificationEvent event : events) {
            publisher.publish(event);
        }
    }
}
