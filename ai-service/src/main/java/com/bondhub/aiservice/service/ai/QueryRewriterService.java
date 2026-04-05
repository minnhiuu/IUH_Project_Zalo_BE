package com.bondhub.aiservice.service.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface QueryRewriterService {

    @SystemMessage("""
    Bạn là chuyên gia tái cấu trúc câu hỏi (Query Rewriter) cho hệ thống RAG.
    Nhiệm vụ: Viết lại câu hỏi hiện tại thành một câu độc lập, đầy đủ thực thể dựa trên lịch sử.

    ═══════════════════════════════════════════════════════════════
    QUY TẮC CỐT LÕI — KẾ THỪA THỰC THỂ (ENTITY CARRYOVER):
    1. ƯU TIÊN TIN NHẮN CUỐI: Luôn coi tin nhắn gần nhất là bối cảnh quan trọng nhất.
    2. KẾ THỪA ĐỊA ĐIỂM NGẦM ĐỊNH: 
       - Nếu User vừa hỏi về một địa điểm (ví dụ: HCM, Hà Nội) ở câu trước.
       - Và câu hiện tại là câu hỏi về thông tin mang tính địa phương (Giá xăng, giá vàng, thời tiết, tỷ giá, tin tức...).
       - THÌ PHẢI tự động đưa địa điểm đó vào câu query mới, kể cả khi User không dùng từ thay thế (đó, kia, này).
    3. CẢM BIẾN CHỦ ĐỀ (TOPIC SENSING):
       - Nếu chủ đề thay đổi hoàn toàn nhưng vẫn là thông tin cần vị trí -> Vẫn giữ vị trí cũ.
       - Ví dụ: "Thời tiết HCM" -> "Giá vàng hiện tại" => "Giá vàng hiện tại tại Hồ Chí Minh".

    QUY TẮC CẤM (TRÁNH NGÁO):
    - KHÔNG thêm địa điểm vào các câu xã giao, chào hỏi, hoặc câu hỏi kiến thức chung.
      Ví dụ: "Thời tiết HCM" -> "Chào bạn" => "Chào bạn" (KHÔNG được thêm HCM).
      Ví dụ: "Giá xăng HN" -> "Bạn là ai?" => "Bạn là ai?" (KHÔNG được thêm HN).
    - KHÔNG rewrite nếu câu hỏi đã có địa điểm khác rõ ràng.
    ═══════════════════════════════════════════════════════════════

    Lịch sử hội thoại (Sắp xếp theo thời gian tăng dần):
    {{conversationHistory}}

    VÍ DỤ THỰC CHIẾN:

    Lịch sử:
      User: Thời tiết ở HCM hôm nay thế nào?
      AI: Thời tiết HCM hôm nay nắng mạnh, 33-35 độ C.
    Câu hỏi: "giá vàng hiện tại"
    → Giá vàng hiện tại tại Hồ Chí Minh là bao nhiêu?
    (VÌ: "Giá vàng" là thông tin nhạy cảm với vị trí, kế thừa "Hồ Chí Minh" từ bối cảnh trước)

    Lịch sử:
      User: Giá xăng tại Hà Nội?
      AI: Giá xăng tại Hà Nội là 23.500đ.
    Câu hỏi: "thời tiết thế nào"
    → Thời tiết tại Hà Nội hiện nay như thế nào?

    Lịch sử:
      User: Cho tôi biết giá vàng SJC tại Đà Nẵng.
      AI: Giá vàng SJC tại Đà Nẵng là...
    Câu hỏi: "Cảm ơn bạn"
    → Cảm ơn bạn
    (VÌ: Câu xã giao không cần địa điểm)
    """)
    String rewrite(
            @V("conversationHistory") String conversationHistory,
            @UserMessage String currentQuery
    );
}
