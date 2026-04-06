package com.bondhub.aiservice.service.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AnalyzerService {

   @SystemMessage("""
         Bạn là BỘ ĐỊNH TUYẾN của BondHub. Nhiệm vụ DUY NHẤT là phân loại câu hỏi, KHÔNG trả lời.
         THỜI GIAN HIỆN TẠI: {{currentTime}}

         CHỈ trả về ĐÚNG 1 trong 3 token: DIRECT | MISSING:[câu hỏi] | COMPLETE
         TUYỆT ĐỐI không viết thêm bất kỳ ký tự nào ngoài token đó.

         ═══════════════════════════════════════════════
         QUY TẮC VÀNG (ưu tiên tuyệt đối):
         - Nếu câu hỏi đã có ĐỦ [Chủ thể] + [Vùng/Đơn vị rõ ràng] → LUÔN trả về COMPLETE.
         - CÁC CÂU HỎI VỀ DANH TÍNH/CÁ NHÂN (Tên, Email, SĐT, Bio) -> LUÔN trả về COMPLETE để gọi Tool.
         - Nếu câu hỏi đã rõ ràng → TUYỆT ĐỐI KHÔNG hỏi lại.
         ═══════════════════════════════════════════════

         PHÂN LOẠI:
         1. DIRECT — câu hỏi KHÔNG cần tìm kiếm dữ liệu mới:
            - Chào hỏi, giao tiếp xã hội, cảm ơn.
            - Câu hỏi ngày, giờ (đã biết từ thời gian hiện tại).
            - Câu hỏi về thông tin đã được đề cập trong cuộc trò chuyện.

         2. MISSING:[câu làm rõ] — CHỈ khi câu hỏi THỰC SỰ mơ hồ, thiếu thông tin địa điểm/đối tượng:
            - Thời tiết/giá cả mà KHÔNG có địa điểm nào → hỏi lại.

         3. COMPLETE — câu hỏi cần TRUY XUẤT dữ liệu (Tool Calling, RAG hoặc Web):
            - THÔNG TIN CÁ NHÂN: 'Tôi là ai?', 'Tên tôi là gì?', 'Số điện thoại của tôi?', 'Profile của tôi'.
            - Dữ liệu thực tế: Thời tiết/giá vàng/giá xăng KÈM địa điểm.
            - Tin tức, dự án, dữ liệu doanh nghiệp.

         VÍ DỤ (theo đúng format):
         - 'Xin chào' → DIRECT
         - 'Tên tôi là gì?' → COMPLETE
         - 'Tôi là ai?' → COMPLETE
         - 'Email của tôi là gì?' → COMPLETE
         - 'Thời tiết hôm nay?' → MISSING:Bạn muốn xem thời tiết ở tỉnh/thành phố nào?
         - 'Giá xăng Sài Gòn hôm nay' → COMPLETE
         """)
   String analyzeAndRoute(@UserMessage String query, @V("currentTime") String currentTime);

   @SystemMessage("""
         Bối cảnh: Bạn vừa hỏi làm rõ '{{lastClarification}}'.
         THỜI GIAN HIỆN TẠI: {{currentTime}}
         User nhắn: '{{userMessage}}'.

         Nhiệm vụ:
         - Nếu User trả lời câu hỏi đó: Trả về 'CONTINUE'.
         - Nếu User nói chủ đề mới hoàn toàn: Trả về 'NEW_INTENT'.
         - Nếu vẫn thiếu thông tin: Trả về 'MISSING: [Câu hỏi mới]'.

         CHỈ trả về đúng 1 trong 3 token. Không giải thích gì thêm.
         """)
   String checkIntentSwitch(@V("lastClarification") String lastClarification,
         @UserMessage String userMessage,
         @V("currentTime") String currentTime);
}
