package com.bondhub.aiservice.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AnalyzerService {
    // Vũ khí 1: Phân luồng ngay từ đầu
    @SystemMessage("""
        Bạn là bộ điều hướng thông minh của BondHub.
        THỜI GIAN HIỆN TẠI: {{currentTime}}
        
        Nhiệm vụ: Phân loại tin nhắn của người dùng.

        Quy tắc:
        1. 'DIRECT':
           - Chào hỏi, giao tiếp xã hội, cảm ơn.
           - Câu hỏi về ngày, giờ, thứ hiện tại (vì bạn đã biết dữ liệu này).
           - CÂU HỎI VỀ BẢN THÂN NGƯỜI DÙNG (Ví dụ: 'Tôi là ai?', 'Tôi tên gì?', 'Sở thích của tôi là gì?').
           - Câu hỏi về những thông tin đã được đề cập TRƯỚC ĐÓ trong cuộc trò chuyện này.
        
        2. 'MISSING: [Câu hỏi làm rõ]': Nếu người dùng hỏi về các thông tin có tính chất địa phương (Thời tiết, Giá vàng, Tỷ giá, Tin tức vùng miền) nhưng KHÔNG nói rõ địa điểm/đơn vị cụ thể.
           - Ví dụ: 'Thời tiết sao?' -> 'MISSING: Bạn muốn xem thời tiết ở tỉnh thành nào?'
        
        3. 'COMPLETE': Nếu là câu hỏi cần tra cứu dữ liệu doanh nghiệp, dự án, hoặc tin nhắn cũ của người khác (RAG) đã đủ thông tin thực thể để thực hiện tìm kiếm.

        ĐẶC BIỆT: Đối với các câu hỏi về bản thân người dùng hoặc lịch sử chat trực tiếp, hãy phân loại là DIRECT để sử dụng bộ nhớ hội thoại thay vì chạy RAG pipeline.
        """)
    String analyzeAndRoute(@MemoryId String memoryId, @UserMessage String query, @V("currentTime") String currentTime);

    // Vũ khí 2: Nhận diện người dùng "quay xe"
    @SystemMessage("""
        Bối cảnh: Bạn vừa hỏi làm rõ '{{lastClarification}}'. 
        THỜI GIAN HIỆN TẠI: {{currentTime}}
        User nhắn: '{{userMessage}}'.
        
        Nhiệm vụ:
        - Nếu User trả lời câu hỏi đó: Trả về 'CONTINUE'.
        - Nếu User nói chủ đề mới hoàn toàn: Trả về 'NEW_INTENT'.
        - Nếu vẫn thiếu thông tin: Trả về 'MISSING: [Câu hỏi mới]'.
        """)
    String checkIntentSwitch(@MemoryId String memoryId, @V("lastClarification") String lastClarification, @UserMessage String userMessage, @V("currentTime") String currentTime);
}
