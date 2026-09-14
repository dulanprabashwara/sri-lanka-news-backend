package lk.srilankannews.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailNotificationProviderTest {

    @Test
    void sendsCompleteUtf8NotificationBodyAndHeaders() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailNotificationProvider provider = new EmailNotificationProvider(
                mailSender, true, "sender@example.com", "http://localhost:3000");
        Notification notification = new Notification(
                "notification-1", "user-1", Notification.NotificationType.STORY_ACTIVITY,
                "story-1", "article-1", "source-1", "publisher", "Publisher",
                List.of(Notification.NotificationReason.FOLLOWED_TOPIC),
                "\u0D85\u0DC0\u0DD4\u0DBB\u0DD4\u0DAF\u0DCA\u0DAF\u0DCF \u0DB4\u0DD4\u0DC0\u0DAD \u0DC3\u0DC4 \u0BA4\u0BAE\u0BBF\u0BB4\u0BCD \u0B9A\u0BC6\u0BAF\u0BCD\u0BA4\u0BBF",
                "\u0D85\u0DC0\u0DD4\u0DBB\u0DD4\u0DAF\u0DCA\u0DAF\u0DCF \u0DC3\u0DCF\u0DBB\u0DCF\u0D82\u0DC1\u0DBA. \u0BA4\u0BAE\u0BBF\u0BB4\u0BCD \u0B9A\u0BC1\u0BB0\u0BC1\u0B95\u0BCD\u0B95\u0BAE\u0BCD.",
                "/story/story-1", "v1", "dedupe-1",
                Instant.parse("2026-09-14T00:00:00Z"), null,
                new Notification.EmailDelivery(
                        Notification.EmailDelivery.DeliveryStatus.PENDING,
                        0, Instant.parse("2026-09-14T00:00:00Z"), null, null),
                null);

        provider.sendNotification(
                notification, "reader@example.com",
                "http://localhost:3000/notifications/unsubscribe?token=safe-token");

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("sender@example.com");
        assertThat(message.getTo()).containsExactly("reader@example.com");
        assertThat(message.getSubject()).contains("\u0D85\u0DC0\u0DD4\u0DBB\u0DD4\u0DAF\u0DCA\u0DAF\u0DCF", "\u0BA4\u0BAE\u0BBF\u0BB4\u0BCD \u0B9A\u0BC6\u0BAF\u0BCD\u0BA4\u0BBF");
        assertThat(message.getText())
                .contains("\u0D85\u0DC0\u0DD4\u0DBB\u0DD4\u0DAF\u0DCA\u0DAF\u0DCF", "\u0BA4\u0BAE\u0BBF\u0BB4\u0BCD \u0B9A\u0BC1\u0BB0\u0BC1\u0B95\u0BCD\u0B95\u0BAE\u0BCD")
                .contains("http://localhost:3000/story/story-1")
                .contains("/notifications/unsubscribe?token=safe-token");
    }
}
