package com.bondhub.userservice.dto.response.user;

import lombok.Builder;

@Builder
public record AccountResponse(
    String id,
    String phoneNumber,
    String email,
    String role
) {}
