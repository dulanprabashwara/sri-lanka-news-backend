/*
 * File overview: Defines the provider contract for Notification Delivery.
 * Area: in-app and email notifications.
 */
package lk.srilankannews.notifications;

public interface NotificationDeliveryProvider {
    boolean isAvailable();
    void sendNotification(Notification notification, String recipientEmail) throws Exception;
}
