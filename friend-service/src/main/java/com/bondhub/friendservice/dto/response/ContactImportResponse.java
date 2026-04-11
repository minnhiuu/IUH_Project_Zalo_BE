package com.bondhub.friendservice.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record ContactImportResponse(
    int totalContacts,
    int normalizedPhones,
    int normalizedEmails,
    int matchedUsers,
    int contactRelationsCreated,
    List<String> matchedUserIds
) {}
