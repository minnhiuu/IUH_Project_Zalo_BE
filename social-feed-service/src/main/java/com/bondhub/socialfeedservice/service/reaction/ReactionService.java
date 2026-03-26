package com.bondhub.socialfeedservice.service.reaction;

import com.bondhub.socialfeedservice.dto.request.reaction.ToggleReactionRequest;
import com.bondhub.socialfeedservice.dto.response.reaction.ReactionResponse;
import com.bondhub.socialfeedservice.dto.response.reaction.ReactionStatsResponse;
import com.bondhub.socialfeedservice.model.enums.ReactionTargetType;
import com.bondhub.socialfeedservice.model.enums.ReactionType;

import java.util.List;

public interface ReactionService {

    ReactionResponse toggleReaction(ToggleReactionRequest request);

    ReactionResponse deleteReaction(String targetId, ReactionTargetType targetType);

    List<ReactionResponse> searchReactions(ReactionTargetType targetType, ReactionType reactionType);

    ReactionStatsResponse getReactionStats(String targetId, ReactionTargetType targetType);
}
