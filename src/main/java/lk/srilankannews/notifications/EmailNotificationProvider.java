package lk.srilankannews.notifications;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationProvider implements NotificationDeliveryProvider {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean emailEnabled;
    private final String publicBaseUrl;

    public EmailNotificationProvider(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${notification.email.enabled:false}") boolean emailEnabled,
            @Value("${notification.email.from:notifications@srilankannews.lk}") String fromAddress,
            @Value("${app.public.base-url:http://localhost:3000}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.emailEnabled = emailEnabled;
        this.fromAddress = fromAddress;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public boolean isAvailable() {
        return emailEnabled && mailSender != null;
    }

    @Override
    public void sendNotification(Notification notification, String recipientEmail) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("Email delivery is not available");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipientEmail);
        message.setSubject("New Story: " + notification.title());

        StringBuilder text = new StringBuilder();
        text.append(notification.title()).append("\n\n");
        text.append(notification.message()).append("\n\n");
        text.append("Source: ").append(notification.sourceName()).append("\n\n");
        text.append("Read more at: ").append(publicBaseUrl).append(notification.linkPath()).append("\n\n");
        
        // Unsubscribe link (will need to sign token in EmailDeliveryWorker)
        // Here we just accept that the caller handles token building, but let's keep it simple:
        // Actually, it's better if EmailDeliveryWorker passes the token or full unsubscribe URL.
        // I will add unsubscribeUrl to the method signature.
        throw new UnsupportedOperationException("Use the overloaded method with unsubscribeUrl");
    }

    public void sendNotification(Notification notification, String recipientEmail, String unsubscribeUrl) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("Email delivery is not available");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipientEmail);
        message.setSubject("New Story: " + notification.title());

        StringBuilder text = new StringBuilder();
        text.append(notification.title()).append("\n\n");
        text.append(notification.message()).append("\n\n");
        text.append("Source: ").append(notification.sourceName()).append("\n\n");
        text.append("Read more at: ").append(publicBaseUrl).append(notification.linkPath()).append("\n\n");
        text.append("--\n");
        text.append("To unsubscribe from these emails, visit: ").append(unsubscribeUrl).append("\n");
        message.setText(text.toString());

        mailSender.send(message);
    }
}
