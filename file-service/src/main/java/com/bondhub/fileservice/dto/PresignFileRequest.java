package com.bondhub.fileservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record PresignFileRequest(
    @NotBlank String fileName,
    @NotBlank String contentType,
    @NotNull Long size,
    @NotBlank String folder
) {}
