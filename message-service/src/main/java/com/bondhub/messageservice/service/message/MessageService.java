package com.bondhub.messageservice.service.message;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.messageservice.dto.response.MessageResponse;

import java.util.List;

public interface MessageService {

    /**
     * Lấy tin nhắn theo conversationId (ObjectId).
     * Kiểm tra quyền truy cập trước khi trả về.
     */
    PageResponse<List<MessageResponse>> findChatMessages(String conversationId, int page, int size);

    /**
     * Gửi tin nhắn vào phòng chat.
     * Kiểm tra currentUser có trong members không.
     */
    void sendMessage(MessageSendRequest request);

    /**
     * Thu hồi tin nhắn (chỉ người gửi).
     */
    void revokeMessage(String messageId);

    /**
     * Xóa tin nhắn chỉ phía mình.
     */
    void deleteMessageForMe(String messageId);
}
