package com.bondhub.searchservice.dto.response;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.ConversationSearchResponse;

import java.util.List;

public record MessageSearchOverviewResponse(
        PageResponse<List<ConversationSearchResponse>> contacts,
        PageResponse<List<MessageSearchResponse>> messages,
        PageResponse<List<MessageSearchResponse>> files
) {}
