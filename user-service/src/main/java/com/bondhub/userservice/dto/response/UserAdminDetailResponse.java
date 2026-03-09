package com.bondhub.userservice.dto.response;

import com.bondhub.userservice.model.enums.Gender;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Full user detail response for admin — includes profile, account, and audit information
 */
@Builder
public record UserAdminDetailResponse(
    // Profile info
    String id,
    String fullName,
    LocalDate dob,
    String bio,
    Gender gender,
    String avatar,
    String background,
    Double backgroundY,

    // Account info (from auth-service)
    String accountId,
    String email,
    String phoneNumber,
    String role,
    Boolean active,
    Boolean isVerified,

    // Audit info
    LocalDateTime createdAt,
    LocalDateTime lastModifiedAt,
    String createdBy,
    String lastModifiedBy,
    LocalDateTime lastLoginAt,

    // Stats
    long totalActivityLogs,

    // Ban info (null if not banned)
    String banReason
) {}
