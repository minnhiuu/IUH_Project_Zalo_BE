package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.model.Conversation;

import java.util.List;

public interface ConversationService {

    /**
     * Tìm hoặc tạo mới cuộc trò chuyện 1-1 giữa 2 user.
     * Đây là entry-point cho "chat mới".
     */
    Conversation getOrCreateDirectConversation(String userA, String userB);

    /**
     * Lấy (hoặc tạo) ConversationResponse cho currentUser với partnerId.
     * Dùng tại endpoint GET /conversations/partner/{partnerId}.
     */
    ConversationResponse getOrCreateConversationForUser(String partnerId);

    /**
     * Lấy danh sách phòng chat của currentUser với phân trang.
     */
    PageResponse<List<ConversationResponse>> getUserConversations(int page, int size);

    /**
     * Đánh dấu đã đọc conversation — kiểm tra quyền truy cập.
     */
    void markAsRead(String conversationId);
}
