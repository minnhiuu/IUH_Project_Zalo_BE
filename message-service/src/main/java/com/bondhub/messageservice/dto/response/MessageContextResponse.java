package com.bondhub.messageservice.dto.response;

import lombok.Builder;

/**
 * Kết quả chứa page index của một message trong conversation.
 * Dùng để FE navigate (scroll-to) đúng đến message khi click từ search result.
 */
@Builder
public record MessageContextResponse(
        int page,           // page index (0-based) chứa messageId này
        int size,           // page size đã dùng để tính
        long totalElements  // tổng số tin nhắn visible (để FE tính totalPages nếu cần)
) {}
