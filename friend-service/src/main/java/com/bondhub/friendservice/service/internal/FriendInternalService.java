package com.bondhub.friendservice.service.internal;

import com.bondhub.common.dto.client.friendservice.UserSearchContextRequest;
import com.bondhub.common.dto.client.friendservice.UserSearchContextResponse;

import java.util.List;

public interface FriendInternalService {

    List<UserSearchContextResponse> getUserSearchContext(UserSearchContextRequest request);
}
