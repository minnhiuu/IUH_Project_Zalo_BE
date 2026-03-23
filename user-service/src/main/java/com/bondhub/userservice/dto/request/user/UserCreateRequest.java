package com.bondhub.userservice.dto.request.user;

import com.bondhub.userservice.model.enums.Gender;
import lombok.Builder;
import java.time.LocalDate;
import java.util.Set;

@Builder
public record UserCreateRequest(
    String fullName,
    LocalDate dob,
    String bio,
    Gender gender,
    String accountId,
    String phoneNumber,
    String role,
    Set<String> initialInterests
) {}
