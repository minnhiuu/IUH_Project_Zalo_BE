package com.leafy.socialfeedservice.dto.response.account;

/**
 * Lightweight DTO for deserialising an account fetched from auth-service.
 * Only the fields needed for mock-data seeding are included.
 */
public record AccountSummaryResponse(
        String id,
        String email
) {}
