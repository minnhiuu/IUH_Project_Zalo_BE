package com.bondhub.messageservice.service.message;

import com.bondhub.common.enums.SystemActionType;

import java.util.Map;
import java.util.Set;

public interface SystemMessageService {

    void sendSystemMessage(String conversationId, String actorId, String actorName, String actorAvatar,
                           SystemActionType action, Map<String, Object> extraMetadata);

    void sendSystemMessage(String conversationId, String actorId, String actorName, String actorAvatar,
                           SystemActionType action, Map<String, Object> extraMetadata,
                           Set<String> recipientUserIds);
}
