package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.request.JoinByLinkRequest;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.dto.response.JoinGroupPreviewResponse;
import com.bondhub.messageservice.dto.response.JoinRequestResponse;

import java.util.List;

public interface JoinRequestService {

    ConversationResponse joinByLink(String token, JoinByLinkRequest request);

    JoinGroupPreviewResponse getJoinPreview(String token);

    void updateJoinQuestion(String conversationId, String question);

    PageResponse<List<JoinRequestResponse>> getJoinRequests(String conversationId, int page, int size);

    ConversationResponse approveJoinRequest(String conversationId, String requestId);

    void rejectJoinRequest(String conversationId, String requestId);

    void cancelMyJoinRequest(String conversationId);
}
