package com.bondhub.messageservice.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateJoinQuestionRequest(
        @Size(max = 250) String question
) {}
