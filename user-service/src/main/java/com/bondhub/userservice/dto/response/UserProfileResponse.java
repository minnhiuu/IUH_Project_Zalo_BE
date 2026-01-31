package com.bondhub.userservice.dto.response;

import com.bondhub.userservice.model.enums.Gender;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileResponse {
    String id;
    String phoneNumber;
    String email;
    String fullName;
    String bio;
    Gender gender;
    LocalDate dob;
    String avatar;
    String background;
}
