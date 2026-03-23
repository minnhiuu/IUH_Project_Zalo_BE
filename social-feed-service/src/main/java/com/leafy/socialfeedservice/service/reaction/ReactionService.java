package com.leafy.socialfeedservice.service.reaction;

import com.leafy.socialfeedservice.dto.request.reaction.ToggleReactionRequest;
import com.leafy.socialfeedservice.dto.response.reaction.ReactionResponse;
import com.leafy.socialfeedservice.dto.response.reaction.ReactionStatsResponse;
import com.leafy.socialfeedservice.model.enums.ReactionTargetType;
import com.leafy.socialfeedservice.model.enums.ReactionType;

import java.util.List;

public interface ReactionService {

    ReactionResponse toggleReaction(ToggleReactionRequest request);

    ReactionResponse deleteReaction(String targetId, ReactionTargetType targetType);

    List<ReactionResponse> searchReactions(ReactionTargetType targetType, ReactionType reactionType);

    ReactionStatsResponse getReactionStats(String targetId, ReactionTargetType targetType);
}
