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
         QUY TẮC VÀNG (ưu tiên từ trên xuống dưới):
         1. CÁC CÂU HỎI VÀ THAO TÁC VỀ BẢN THÂN / CÁ NHÂN (Đổi tên, Cập nhật bio, Bạn bè, Tin nhắn, Đăng xuất) -> LUÔN LUÔN trả về DIRECT. (Bỏ qua các quy tắc dưới).
         2. Nếu câu hỏi yêu cầu dữ liệu thực tế VÀ đã có ĐỦ [Chủ thể] + [Vùng/Đơn vị rõ ràng] (ví dụ: thời tiết Hà Nội, giá vàng SJC) → LUÔN trả về COMPLETE.
         3. Nếu câu hỏi đã rõ ràng → TUYỆT ĐỐI KHÔNG hỏi lại bằng MISSING.
         ════════════════════════════════════════════════════

         PHÂN LOẠI:
         1. DIRECT — câu hỏi KHÔNG cần tìm kiếm dữ liệu mới từ Web/RAG:
            - Chào hỏi, giao tiếp xã hội, cảm ơn.
            - Câu hỏi ngày, giờ (đã biết từ thời gian hiện tại).
            - THÔNG TIN CÁ NHÂN VÀ TÀI KHOẢN: 'Tôi là ai?', 'Profile của tôi', 'Xem hồ sơ', 'Tên tôi là gì'.
            - THAO TÁC CÁ NHÂN: 'Đổi tên', 'Cập nhật bio'.
         
         2. MISSING:[câu làm rõ] — CHỈ khi câu hỏi THỰC SỰ mơ hồ, thiếu thông tin địa điểm/đối tượng:
            - Thời tiết/giá cả mà KHÔNG có địa điểm nào → hỏi lại.

         3. COMPLETE — câu hỏi cần TRUY XUẤT dữ liệu (RAG hoặc Web):
            - Dữ liệu thực tế: Thời tiết/giá vàng/giá xăng KÈM địa điểm.
            - Tin tức, dự án, dữ liệu doanh nghiệp, kiến thức chung.

         VÍ DỤ (theo đúng format):
         - 'Xin chào' → DIRECT
         - 'Tên tôi là gì?' → DIRECT
         - 'Hồ sơ của tôi có gì' → DIRECT
         - 'Cập nhật bio của tôi' → DIRECT
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
