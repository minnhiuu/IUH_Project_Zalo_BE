package com.bondhub.userservice.service.elasticsearch;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserSearchService {
    PageResponse<List<UserSummaryResponse>> searchUsers(String keyword, Pageable pageable);
}
