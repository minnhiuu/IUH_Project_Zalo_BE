package com.bondhub.searchservice.dto.response;

import lombok.Builder;

@Builder
public record AccountResponse(
    String id,
    String phoneNumber,
    String email,
    String role
) {}
