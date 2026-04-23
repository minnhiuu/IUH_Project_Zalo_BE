package com.bondhub.userservice.dto.request.user;

import org.springframework.web.multipart.MultipartFile;
import lombok.Builder;
import jakarta.annotation.Nullable;

@Builder
public record AvatarUpdateRequest(
    @Nullable MultipartFile file,
    @Nullable String imageKey
) {}
