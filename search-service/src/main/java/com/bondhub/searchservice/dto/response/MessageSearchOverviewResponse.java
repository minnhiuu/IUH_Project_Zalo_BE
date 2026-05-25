package com.bondhub.searchservice.dto.response;

import com.bondhub.common.dto.PageResponse;

import java.util.List;

public record MessageSearchOverviewResponse(
        PageResponse<List<MessageSearchResponse>> messages,
        PageResponse<List<MessageSearchResponse>> files
) {}
