package com.bondhub.common.dto.client.userservice.user.request;

import lombok.Builder;
import java.time.LocalDate;

@Builder
public record UserUpdateRequest(
    String fullName,
    LocalDate dob,
    String bio,
    String gender
) {}
