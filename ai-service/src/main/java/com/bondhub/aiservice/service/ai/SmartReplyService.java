package com.bondhub.aiservice.service.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;

public interface SmartReplyService {
    @SystemMessage({
        "Bạn là một chuyên gia gợi ý trả lời tin nhắn trên nền tảng BondHub.",
        "Dựa trên danh sách các tin nhắn gần đây, hãy gợi ý 3 câu trả lời ngắn gọn, tự nhiên, và phù hợp với ngữ cảnh.",
        "Kết quả trả về PHẢI là định dạng JSON như sau: { \"replies\": [\"câu 1\", \"câu 2\", \"câu 3\"] }"
    })
    @UserMessage("Hãy gợi ý trả lời cho cuộc hội thoại sau: {{messages}}")
    String generateReplies(@V("messages") List<String> messages);
}
