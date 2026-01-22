package com.bondhub.userservice.dto.request;

import com.bondhub.userservice.model.enums.Gender;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserUpdateRequest {
    String fullName;
    LocalDate dob;
    String bio;
    Gender gender;
}
