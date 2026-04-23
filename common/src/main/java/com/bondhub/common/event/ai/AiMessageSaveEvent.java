package com.bondhub.common.event.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageSaveEvent {
    private String userId; // ID của người dùng (human)
    private String chatId; // conversationId (MongoDB)
    private String content; // Nội dung tin nhắn (thô/generator response)
    private String senderId; // ID của người gửi (có thể là userId hoặc "ai-assistant-001")
    private String senderName; // Tên hiển thị của sender (AI Name)
    private String type; // Loại tin nhắn (CHAT, IMAGE...)
}
