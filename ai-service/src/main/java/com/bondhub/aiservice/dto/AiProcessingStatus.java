package com.bondhub.aiservice.dto;

/**
 * Enum trạng thái xử lý AI Pipeline.
 * Được gửi qua SSE stream với type="STATUS" để Frontend hiển thị trạng thái real-time.
 * Frontend sẽ dùng name() của enum này làm i18n key.
 */
public enum AiProcessingStatus {
    ANALYZING_INTENT,   // Đang phân tích ý định người dùng
    RETRIEVING_VECTOR,  // Đang tìm kiếm trong bộ nhớ nội bộ (Qdrant)
    GRADING_DATA,       // Đang thẩm định độ chính xác dữ liệu
    WEB_SEARCHING,      // Đang tìm kiếm trên Internet (Tavily)
    GENERATING_ANSWER   // Đang tổng hợp và soạn câu trả lời
}
