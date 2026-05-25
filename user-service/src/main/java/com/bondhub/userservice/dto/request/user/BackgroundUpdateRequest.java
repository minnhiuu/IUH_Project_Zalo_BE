package com.bondhub.userservice.dto.request.user;

import org.springframework.web.multipart.MultipartFile;
import lombok.Builder;
import jakarta.annotation.Nullable;

@Builder
public record BackgroundUpdateRequest(
    @Nullable MultipartFile file,
    @Nullable String imageKey,
    Double y
) {}
