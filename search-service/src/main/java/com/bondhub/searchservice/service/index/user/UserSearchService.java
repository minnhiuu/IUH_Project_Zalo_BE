package com.bondhub.searchservice.service.index.user;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.searchservice.dto.response.UserSearchResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserSearchService {
    PageResponse<List<UserSearchResponse>> searchUsersWithMetadata(String keyword, Pageable pageable);

    PageResponse<List<UserSummaryResponse>> searchUsers(String keyword, Pageable pageable);

    List<UserSummaryResponse> findUsersByPhones(List<String> phones);
}
