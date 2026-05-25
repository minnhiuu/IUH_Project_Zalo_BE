package com.bondhub.userservice.dto.request.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record AccountUpdateRequest(
        String phoneNumber,
        String password,
        String email
) {}
