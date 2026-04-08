package com.bondhub.messageservice.service.message;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.common.enums.SystemActionType;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.model.Message;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void sendMessage(String conversationId, MessageSendRequest request);

    /**
     * Thu hồi tin nhắn (chỉ người gửi).
     */
    void revokeMessage(String messageId);

    /**
     * Xóa tin nhắn chỉ phía mình.
     */
    void deleteMessageForMe(String messageId);

    void sendSystemMessage(String conversationId, String actorId, String actorName,
                                   SystemActionType action, Map<String, Object> extraMetadata);

    void sendSystemMessage(String conversationId, String actorId, String actorName,
                                   SystemActionType action, Map<String, Object> extraMetadata,
                                   Set<String> recipientUserIds);

    void deleteAllMessagesByConversationId(String conversationId);
    
    void deleteAllMessagesByConversationIdForMe(String conversationId);
}
