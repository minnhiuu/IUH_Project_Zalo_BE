package com.leafy.socialfeedservice.dto.request.reaction;

import com.leafy.socialfeedservice.model.enums.ReactionTargetType;
import com.leafy.socialfeedservice.model.enums.ReactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record ToggleReactionRequest(
        @NotBlank(message = "reaction.targetId.required")
        String targetId,
        @NotNull(message = "reaction.targetType.required")
        ReactionTargetType targetType,
        @NotNull(message = "reaction.type.required")
        ReactionType type
) {
}
