package com.bondhub.userservice.dto.request;

import com.bondhub.userservice.model.enums.Gender;
import lombok.Builder;
import java.time.LocalDate;

@Builder
public record UserCreateRequest(
    String fullName,
    LocalDate dob,
    String bio,
    Gender gender,
    String accountId
) {}
