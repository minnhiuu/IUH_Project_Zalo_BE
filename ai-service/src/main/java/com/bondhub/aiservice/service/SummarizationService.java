package com.bondhub.aiservice.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface SummarizationService {
    @SystemMessage({
        "Bạn là trợ lý tóm tắt hội thoại của BondHub.",
        "Hãy tóm tắt nội dung hội thoại được cung cấp thành 3-5 ý chính ngắn gọn, súc tích (dùng dấu gạch đầu dòng)."
    })
    @UserMessage("Tóm tắt hội thoại này: {{text}}")
    String summarize(String text);
}
