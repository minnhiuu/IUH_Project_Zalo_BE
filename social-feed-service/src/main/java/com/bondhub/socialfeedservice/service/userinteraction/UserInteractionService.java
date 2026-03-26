package com.bondhub.socialfeedservice.service.userinteraction;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.socialfeedservice.dto.response.interaction.UserInteractionResponse;

import java.util.List;

public interface UserInteractionService {

    PageResponse<List<UserInteractionResponse>> getMyInteractions(int page, int size);

    PageResponse<List<UserInteractionResponse>> getInteractionsByPost(String postId, int page, int size);

    List<UserInteractionResponse> getNewestInteractionsByUser(String userId, int limit);

    void recordView(String postId);
}
