package com.bondhub.messageservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record UpdateExpirationRequest(
        @Min(value = 0, message = "Expiration days cannot be negative")
        @Max(value = 30, message = "Expiration days cannot exceed 30")
        Integer days
) {
}
