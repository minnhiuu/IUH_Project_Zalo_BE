package com.bondhub.aiservice.client.userservice;

/** DTO tối giản cho profile của currentUser */
public record UserProfileSummary(
        String userId,
        String displayName,
        String email,
        String phoneNumber,
        String avatarUrl,
        String bio
) {}
