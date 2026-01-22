package com.bondhub.common.dto.client.userservice.user.response;

import com.bondhub.common.enums.Gender;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String id;
    String fullName;
    LocalDate dob;
    String bio;
    Gender gender;
    String accountId;
}
