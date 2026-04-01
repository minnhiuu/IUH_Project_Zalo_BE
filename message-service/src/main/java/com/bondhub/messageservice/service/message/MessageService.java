package com.bondhub.messageservice.service.message;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.MessageSendRequest;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.model.Message;

import java.util.List;

public interface MessageService {
    Message save(Message message);

    PageResponse<List<MessageResponse>> findChatMessages(String recipientId, int page, int size);

    void sendMessage(MessageSendRequest request);
    void revokeMessage(String messageId);
    void deleteMessageForMe(String messageId);
}
