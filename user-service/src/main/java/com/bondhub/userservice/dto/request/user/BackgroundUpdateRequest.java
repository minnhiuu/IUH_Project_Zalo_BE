package com.bondhub.userservice.dto.request.user;

import org.springframework.web.multipart.MultipartFile;
import lombok.Builder;

@Builder
public record BackgroundUpdateRequest(
    MultipartFile file,
    Double y
) {}
