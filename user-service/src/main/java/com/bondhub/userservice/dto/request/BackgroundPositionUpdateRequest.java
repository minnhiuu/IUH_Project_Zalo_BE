package com.bondhub.userservice.dto.request;

import lombok.Builder;

@Builder
public record BackgroundPositionUpdateRequest(
        Double y
) {}
