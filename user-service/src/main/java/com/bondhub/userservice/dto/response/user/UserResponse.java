package com.bondhub.userservice.dto.response.user;

import com.bondhub.userservice.model.enums.Gender;
import lombok.Builder;
import java.time.LocalDate;

@Builder
public record UserResponse(
    String id,
    String fullName,
    LocalDate dob,
    String bio,
    Gender gender,
    AccountResponse accountInfo,
    String avatar,
    String background,
    Double backgroundY
) {}
