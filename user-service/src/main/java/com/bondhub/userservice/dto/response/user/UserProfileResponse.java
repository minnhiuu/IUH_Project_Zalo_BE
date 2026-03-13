package com.bondhub.userservice.dto.response.user;

import com.bondhub.userservice.model.enums.Gender;
import lombok.Builder;
import java.time.LocalDate;

@Builder
public record UserProfileResponse(
    String id,
    String phoneNumber,
    String email,
    String role,
    String fullName,
    String bio,
    Gender gender,
    LocalDate dob,
    String avatar,
    String background,
    Double backgroundY
) {}
