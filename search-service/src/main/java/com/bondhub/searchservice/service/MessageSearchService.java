package com.bondhub.searchservice.service;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.searchservice.dto.request.MessageSearchRequest;
import com.bondhub.searchservice.dto.response.MessageSearchResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MessageSearchService {
    PageResponse<List<MessageSearchResponse>> searchMessages(String userId, MessageSearchRequest request, Pageable pageable);
}
