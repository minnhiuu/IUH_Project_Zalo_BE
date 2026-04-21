package com.bondhub.searchservice.service.message;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.searchservice.dto.request.MessageSearchRequest;
import com.bondhub.searchservice.dto.response.MessageSearchOverviewResponse;
import com.bondhub.searchservice.dto.response.MessageSearchResponse;
import com.bondhub.searchservice.enums.MessageSearchSection;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MessageSearchService {
    PageResponse<List<MessageSearchResponse>> searchMessages(
            String userId,
            MessageSearchRequest request,
            MessageSearchSection section,
            Pageable pageable);

    MessageSearchOverviewResponse searchMessageOverview(
            String userId,
            MessageSearchRequest request,
            int sectionSize);
}
