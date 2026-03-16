package com.bondhub.notificationservices.dto.response.notification;

public record NotificationAcceptedResponse(
        boolean batched,
        String message
) {
    public static NotificationAcceptedResponse queued() {
        return new NotificationAcceptedResponse(true, "Notification queued for batched delivery");
    }

    public static NotificationAcceptedResponse immediate() {
        return new NotificationAcceptedResponse(false, "Notification delivered immediately");
    }
}
