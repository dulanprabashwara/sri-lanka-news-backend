package lk.srilankannews.notifications;

public interface NotificationDeliveryProvider {
    boolean isAvailable();
    void sendNotification(Notification notification, String recipientEmail) throws Exception;
}
