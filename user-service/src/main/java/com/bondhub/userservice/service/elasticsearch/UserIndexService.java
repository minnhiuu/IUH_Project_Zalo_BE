package com.bondhub.userservice.service.elasticsearch;

import com.bondhub.userservice.dto.request.UserIndexRequest;

public interface UserIndexService {
    void indexUser(UserIndexRequest request);
    void deleteByUserId(String userId);
}
