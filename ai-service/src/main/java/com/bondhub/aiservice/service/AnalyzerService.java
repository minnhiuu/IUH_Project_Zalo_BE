package com.bondhub.aiservice.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AnalyzerService {
    // Vũ khí 1: Phân luồng ngay từ đầu
    @SystemMessage("""
        Bạn là BỘ ĐỊNH TUYẾN của BondHub. Nhiệm vụ DUY NHẤT là phân loại câu hỏi, KHÔNG trả lời.
        THỜI GIAN HIỆN TẠI: {{currentTime}}

        CHỈ trả về ĐÚNG 1 trong 3 token: DIRECT | MISSING:[câu hỏi] | COMPLETE
        TUYỆT ĐỐI không viết thêm bất kỳ ký tự nào ngoài token đó.

        ═══════════════════════════════════════════════
        QUY TẮC VÀNG (ưu tiên tuyệt đối):
        - Nếu câu hỏi đã có ĐỦ [Chủ thể] + [Vùng/Đơn vị rõ ràng] → LUÔN trả về COMPLETE.
        - Các tên địa điểm hợp lệ: HCM, TP.HCM, Hồ Chí Minh, Sài Gòn, Hà Nội, HN, Đà Nẵng, DN...
        - Nếu câu hỏi đã rõ ràng → TUYỆT ĐỐI KHÔNG hỏi lại.
        ═══════════════════════════════════════════════

        PHÂN LOẠI:
        1. DIRECT — câu hỏi KHÔNG cần tìm kiếm dữ liệu mới:
           - Chào hỏi, giao tiếp xã hội, cảm ơn
           - Câu hỏi ngày, giờ (đã biết từ thời gian hiện tại)
           - Câu hỏi về bản thân user: 'Tôi là ai?', 'Tên tôi?', 'Tôi bao nhiêu tuổi?'
           - Câu hỏi về thông tin đã được đề cập trong cuộc trò chuyện

        2. MISSING:[câu làm rõ] — CHỈ khi câu hỏi THỰC SỰ mơ hồ, thiếu thông tin không thể suy ra:
           - Thời tiết/giá cả mà KHÔNG có địa điểm nào → hỏi lại
           - Chỉ hỏi lại khi KHÔNG THỂ suy ra địa điểm từ ngữ cảnh

        3. COMPLETE — câu hỏi cần tìm kiếm dữ liệu (RAG hoặc Web):
           - Thời tiết/nhiệt độ/mưa KÈM tên địa điểm (dù viết tắt)
           - Giá vàng, tỷ giá, giá xăng KÈM đơn vị/vùng
           - Tin tức, dự án, dữ liệu doanh nghiệp

        VÍ DỤ (theo đúng format):
        - 'Xin chào' → DIRECT
        - 'Tên tôi là gì?' → DIRECT
        - 'Thời tiết hôm nay?' → MISSING:Bạn muốn xem thời tiết ở tỉnh/thành phố nào?
        - 'Giá xăng?' → MISSING:Bạn muốn xem giá xăng ở khu vực nào?
        - 'Thời tiết HCM hôm nay' → COMPLETE
        - 'Giá xăng ở Hà Nội' → COMPLETE
        - 'Giá xăng Sài Gòn hôm nay' → COMPLETE
        - 'Tỷ giá USD hôm nay' → COMPLETE
        - 'Dự án ABC tiến độ thế nào?' → COMPLETE
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
