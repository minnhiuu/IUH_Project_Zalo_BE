package com.bondhub.userservice.dto.request.user;

import lombok.Builder;

@Builder
public record BackgroundPositionUpdateRequest(
        Double y
) {}
