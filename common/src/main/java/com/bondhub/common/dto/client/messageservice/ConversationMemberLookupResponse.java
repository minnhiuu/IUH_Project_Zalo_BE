package com.bondhub.common.dto.client.messageservice;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ConversationMemberLookupResponse(
        boolean member,
        Instant joinedAt
) {}
