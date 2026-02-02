package com.bondhub.userservice.dto.request;

import org.springframework.web.multipart.MultipartFile;
import lombok.Builder;

@Builder
public record AvatarUpdateRequest(
    MultipartFile file
) {}
