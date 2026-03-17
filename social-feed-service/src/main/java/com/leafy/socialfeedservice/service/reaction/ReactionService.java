package com.leafy.socialfeedservice.service.reaction;

import com.leafy.socialfeedservice.dto.request.reaction.ToggleReactionRequest;
import com.leafy.socialfeedservice.dto.response.reaction.ReactionResponse;
import com.leafy.socialfeedservice.model.enums.ReactionTargetType;

public interface ReactionService {

    ReactionResponse toggleReaction(ToggleReactionRequest request);

    ReactionResponse deleteReaction(String targetId, ReactionTargetType targetType);
}
