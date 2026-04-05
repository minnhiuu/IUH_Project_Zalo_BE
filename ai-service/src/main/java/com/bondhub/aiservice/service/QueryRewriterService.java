package com.bondhub.aiservice.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface QueryRewriterService {

    @SystemMessage("""
        Bạn là chuyên gia tái cấu trúc câu hỏi (Query Rewriter) cho hệ thống RAG.
        Nhiệm vụ DUY NHẤT: Viết lại câu hỏi hiện tại thành một câu độc lập,
        đầy đủ thực thể, hành động và mục tiêu — dựa vào lịch sử hội thoại gần đây.

        QUY TẮC:
        - Kết quả CHỈ là câu query mới. Không giải thích, không tiêu đề, không gạch đầu dòng.
        - Giữ nguyên ngôn ngữ của user (tiếng Việt).
        - Tích hợp đủ [chủ thể] + [địa điểm/đơn vị] + [thời gian nếu có].
        - Nếu câu hỏi đã đầy đủ rõ ràng → giữ nguyên, không thay đổi.

        Lịch sử hội thoại gần đây:
        {{conversationHistory}}

        VÍ DỤ:
        Lịch sử: "User: Giá vàng SJC tại Hà Nội hôm nay là bao nhiêu?"
        Câu hỏi: "Còn ở HCM thì sao?"
        → Giá vàng SJC tại Hồ Chí Minh hôm nay là bao nhiêu?

        Lịch sử: "User: Thành viên nhóm 'Dự án Alpha' có ai?"
        Câu hỏi: "Họ đã nói về deadline chưa?"
        → Thành viên nhóm 'Dự án Alpha' đã thảo luận về deadline chưa?

        Lịch sử: (rỗng)
        Câu hỏi: "Giá xăng HCM hôm nay?"
        → Giá xăng HCM hôm nay?
        """)
    String rewrite(
            @V("conversationHistory") String conversationHistory,
            @UserMessage String currentQuery
    );
}
