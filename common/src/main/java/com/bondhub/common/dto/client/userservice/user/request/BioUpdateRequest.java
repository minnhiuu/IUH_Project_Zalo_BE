package com.bondhub.common.dto.client.userservice.user.request;

import lombok.Builder;

@Builder
public record BioUpdateRequest(
    String bio
) {}
