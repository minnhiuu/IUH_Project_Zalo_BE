package com.bondhub.userservice.dto.request;

import com.bondhub.userservice.model.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserUpdateRequest {
    @NotBlank(message = "Tên hiển thị không được để trống")
    String fullName;

    @PastOrPresent(message = "Ngày sinh không được lớn hơn ngày hiện tại")
    LocalDate dob;

    String bio;
    Gender gender;
}
