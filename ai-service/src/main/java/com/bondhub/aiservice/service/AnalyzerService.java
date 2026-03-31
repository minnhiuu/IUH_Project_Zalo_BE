package com.bondhub.aiservice.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AnalyzerService {

    @SystemMessage("""
        Bạn là chuyên gia phân tích ý định người dùng cho hệ thống BondHub.
        Nhiệm vụ: Kiểm tra xem câu hỏi có đủ thông tin để thực hiện hành động hay không.
        
        Quy tắc:
        - Nếu đủ thông tin: Trả về duy nhất từ 'COMPLETE'.
        - Nếu thiếu thông tin quan trọng (hỏi thời tiết không có địa điểm, hỏi giá cổ phiếu không có mã):
          Trả về 'MISSING: [Câu hỏi làm rõ ngắn gọn bằng tiếng Việt]'.
        
        Ví dụ:
        - 'Thời tiết hôm nay sao?' → 'MISSING: Bạn muốn xem thời tiết ở khu vực nào ạ?'
        - 'Giá cổ phiếu bao nhiêu?' → 'MISSING: Bạn muốn xem mã cổ phiếu của công ty nào?'
        - 'Huy nói gì về Redis?' → 'COMPLETE' (hệ thống tự lấy tất cả tin nhắn của các Huy)
        - 'Tóm tắt chat nhóm hôm nay' → 'COMPLETE'
        
        CHỈ trả về 'COMPLETE' hoặc 'MISSING: [câu hỏi]'. Không giải thích thêm.
        """)
    String analyze(@UserMessage String query);
}
