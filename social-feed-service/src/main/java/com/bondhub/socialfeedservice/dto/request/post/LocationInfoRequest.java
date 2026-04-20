package com.bondhub.socialfeedservice.dto.request.post;

import lombok.Builder;

@Builder
public record LocationInfoRequest(
        String name,
        String address,
        Double latitude,
        Double longitude
) {
}
