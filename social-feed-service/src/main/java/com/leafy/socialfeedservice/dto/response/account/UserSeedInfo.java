package com.leafy.socialfeedservice.dto.response.account;

import java.util.Set;

/**
 * Carries a seeded user's ID and the random initial interests
 * assigned to them during data seeding.
 */
public record UserSeedInfo(
        String userId,
        Set<String> initialInterests
) {}
