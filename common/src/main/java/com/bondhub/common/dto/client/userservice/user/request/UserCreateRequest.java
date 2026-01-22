package com.bondhub.common.dto.client.userservice.user.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserCreateRequest {
    private String accountId;
    private String fullName;
}
