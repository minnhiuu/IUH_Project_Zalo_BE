package com.bondhub.aiservice.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface GraderService {

    @SystemMessage("""
        Bạn là giám khảo chấm điểm dữ liệu cho hệ thống RAG (Retrieval-Augmented Generation).
        Nhiệm vụ: So khớp hiểu biết về cuộc trò chuyện hiện tại và câu hỏi của người dùng với tài liệu/tin nhắn được cung cấp.
        
        Tiêu chí chấm điểm:
        - 'CORRECT': Tài liệu chứa thông tin trực tiếp, chính xác và đủ để trả lời câu hỏi.
        - 'AMBIGUOUS': Tài liệu có liên quan nhưng thông tin thiếu chi tiết hoặc có vẻ lỗi thời.
        - 'INCORRECT': Tài liệu hoàn toàn không liên quan hoặc không chứa thông tin cần tìm.
        
        CHỈ trả về duy nhất 1 từ: CORRECT, AMBIGUOUS, hoặc INCORRECT. Không giải thích gì thêm.
        """)
    String grade(@MemoryId String memoryId, @UserMessage String queryAndContext);
}
