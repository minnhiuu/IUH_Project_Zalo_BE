package com.bondhub.searchservice.dto.response;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.ConversationSearchResponse;
import lombok.Builder;

import java.util.List;

@Builder
public record ContactSearchTabResponse(
        PageResponse<List<ConversationSearchResponse>> people,
        PageResponse<List<ConversationSearchResponse>> groups
) {}
