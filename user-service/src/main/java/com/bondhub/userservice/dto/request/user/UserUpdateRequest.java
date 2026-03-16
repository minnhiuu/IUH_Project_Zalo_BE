package com.bondhub.userservice.dto.request.user;

import com.bondhub.userservice.model.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Builder;
import java.time.LocalDate;

@Builder
public record UserUpdateRequest(
    @NotBlank(message = "{user.update.fullNameRequired}")
    String fullName,

    @PastOrPresent(message = "{user.update.dobInvalid}")
    LocalDate dob,

    String bio,
    Gender gender
) {}
