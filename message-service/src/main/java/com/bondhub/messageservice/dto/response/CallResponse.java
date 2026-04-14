package com.bondhub.messageservice.dto.response;

import lombok.Builder;
import lombok.With;

@Builder(toBuilder = true)
@With
public record CallResponse(
        String sessionId,
        String roomId,
        String rtcToken,
        long appId,
        String callerId,
        String callerName,
        String callerAvatar,
        String receiverId,
        String receiverName,
        String receiverAvatar
) {
}
