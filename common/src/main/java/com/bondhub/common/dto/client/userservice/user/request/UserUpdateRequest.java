package com.bondhub.common.dto.client.userservice.user.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record UserUpdateRequest(
    String fullName,
    LocalDate dob,
    String bio,
    String gender
) {}
