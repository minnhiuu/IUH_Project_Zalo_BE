package com.bondhub.common.dto.client.userservice.user.response;

import com.bondhub.common.enums.SearchVisibility;
import lombok.Builder;

@Builder
public record UserSearchVisibilityResponse(
        String userId,
        SearchVisibility nameSearchVisibility,
        SearchVisibility phoneSearchVisibility
) {
}
