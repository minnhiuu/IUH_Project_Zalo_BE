package com.bondhub.common.dto.client.userservice.user.response;

import com.bondhub.common.enums.Gender;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record UserResponse(
    String id,
    String fullName,
    LocalDate dob,
    String bio,
    Gender gender,
    String accountId
) {}
