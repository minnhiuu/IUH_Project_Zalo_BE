package com.bondhub.messageservice.service.message;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.dto.response.CursorPageResponse;
import com.bondhub.messageservice.dto.response.MessageSeenResponse;
import com.bondhub.messageservice.model.Message;
import java.util.List;

public interface MessageService {

    /**
     * Lấy tin nhắn theo conversationId (ObjectId).
     * Kiểm tra quyền truy cập trước khi trả về.
     */
    PageResponse<List<MessageResponse>> findChatMessages(String conversationId, int page, int size);

    /**
     * Lấy tin nhắn lọc theo loại (IMAGE, VIDEO, FILE, LINK).
     */
    PageResponse<List<MessageResponse>> findMediaMessages(String conversationId, List<String> types, int page, int size);

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

    /**
     * Toggle reaction (thêm nếu chưa có, xóa nếu đã có).
     */
    void toggleReaction(String messageId, String emoji);

    /**
     * Lấy danh sách tin nhắn từ một mốc thời gian (sinceId).
     * Phục vụ chức năng tóm tắt AI.
     */
    List<MessageResponse> getMessagesSince(String conversationId, String sinceId, String userId);

    /**
     * Xóa toàn bộ reaction của current user khỏi tin nhắn.
     */
    void removeAllMyReactions(String messageId);

    /**
     * Xóa tin nhắn của thành viên trong nhóm (Admin/Owner).
     * Admin không được xóa tin nhắn của Owner.
     */
    void deleteGroupMemberMessage(String conversationId, String messageId);

    /**
     * Lấy danh sách thành viên đã xem một tin nhắn trong nhóm.
     * Loại trừ người gửi tin nhắn.
     */
    List<MessageSeenResponse> getSeenMembers(String conversationId, String messageId);

    /**
     * Lấy tin nhắn theo conversationId với Cursor-based pagination (V2).
     * Hỗ trợ lướt lên, lướt xuống và nhảy tới tin nhắn cụ thể.
     */
    CursorPageResponse<MessageResponse> findChatMessagesV2(
            String conversationId, String cursor, int limit, String direction, String aroundMessageId);
}
