package com.bondhub.notificationservices.service.dnd;

import com.bondhub.notificationservices.event.BatchedNotificationEvent;

public interface AutoReplyService {

    void replyIfNeeded(BatchedNotificationEvent event);
}
