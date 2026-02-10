package com.bondhub.userservice.service.user;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserSearchService {
    PageResponse<List<UserSummaryResponse>> searchUsers(String keyword, Pageable pageable);
    void saveToToIndex(UserIndex userIndex);
    void deleteFromIndex(String userId);
}
