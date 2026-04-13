package com.bondhub.socketservice.controller;

import com.bondhub.socketservice.dto.TypingPayload;
import com.bondhub.socketservice.service.TypingService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class TypingController {

    private final TypingService typingService;

    @MessageMapping("/chat.typing")
    public void typing(
            @Payload TypingPayload payload,
            SimpMessageHeaderAccessor headerAccessor) {
        String senderId = (String) headerAccessor.getSessionAttributes().get("userId");
        if (senderId == null || payload.conversationId() == null) return;

        // Build a new payload with the resolved senderId
        TypingPayload enriched = new TypingPayload(
                payload.conversationId(),
                senderId,
                payload.userName(),
                payload.isTyping(),
                payload.platform()
        );
        typingService.broadcast(enriched, senderId);
    }
}
