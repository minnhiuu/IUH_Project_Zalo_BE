package com.bondhub.aiservice.service.core.stream;

import reactor.core.publisher.Flux;

public interface AiStreamService {

    /**
     * Stream chat response từ BondHubAssistant (non-agentic, simple RAG).
     */
    Flux<String> streamChat(String userId, String chatId, String userMessage);
}
