package com.bondhub.socketservice.service;

import com.bondhub.socketservice.dto.TypingPayload;

public interface TypingService {
    void broadcast(TypingPayload payload, String senderId);
}
