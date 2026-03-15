package com.bondhub.userservice.service.user;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.dto.response.UserSyncResponse;
import java.util.List;

public interface UserInternalService {
    UserSummaryResponse getUserSummaryByAccountId(String accountId);

    long getUserCount();

    UserSyncResponse getUserById(String userId);

    List<UserSyncResponse> getUsersBatch(String lastId, int size);

    void recordLastLogin(String accountId);

    void syncBanStatus(String accountId, boolean banned);
}
