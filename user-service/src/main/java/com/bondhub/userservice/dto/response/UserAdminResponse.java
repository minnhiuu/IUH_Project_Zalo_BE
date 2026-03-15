package com.bondhub.userservice.dto.response;

import com.bondhub.userservice.dto.response.user.UserResponse;
import lombok.Builder;

/**
 * Response containing user information with audit details for admin purposes
 */
@Builder
public record UserAdminResponse(
    UserResponse user,
    AuditResponse audit
) {}
