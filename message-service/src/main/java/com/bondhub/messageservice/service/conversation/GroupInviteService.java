package com.bondhub.messageservice.service.conversation;

import com.bondhub.messageservice.dto.request.GroupInviteSendRequest;

public interface GroupInviteService {
    void sendInvites(String conversationId, GroupInviteSendRequest request);
}
