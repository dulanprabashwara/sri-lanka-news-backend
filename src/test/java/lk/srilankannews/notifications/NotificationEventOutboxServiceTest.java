package lk.srilankannews.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class NotificationEventOutboxServiceTest {

    private NotificationEventRepository repository;
    private NotificationEventPublisher publisher;
    private Clock clock;
    private NotificationEventOutboxService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationEventRepository.class);
        publisher = mock(NotificationEventPublisher.class);
        clock = Clock.fixed(Instant.parse("2026-09-04T10:00:00Z"), ZoneId.of("UTC"));
        service = new NotificationEventOutboxService(repository, publisher, clock);
    }

    @Test
    void shouldSaveAndPublishWhenEventDoesNotExist() {
        when(repository.existsByArticleIdAndEventVersion("art1", "v1")).thenReturn(false);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.dispatch("art1", "st1", "src1", "v1");

        verify(repository).save(argThat(event -> 
            "art1".equals(event.articleId()) && "v1".equals(event.eventVersion())
        ));
        verify(publisher).publish(any());
    }

    @Test
    void shouldNotSaveOrPublishWhenEventAlreadyExistsIdempotency() {
        when(repository.existsByArticleIdAndEventVersion("art1", "v1")).thenReturn(true);

        service.dispatch("art1", "st1", "src1", "v1");

        verify(repository, never()).save(any());
        verify(publisher, never()).publish(any());
    }
}
