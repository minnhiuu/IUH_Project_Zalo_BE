package com.bondhub.aiservice.service.core.crag;

import reactor.core.publisher.Flux;

import java.util.Map;

public interface AgenticCragService {

    /**
     * Entry point cho agentic chat pipeline.
     * Nhận query, xử lý qua 3 lớp (Social → Rewrite → Analyze/Route),
     * và trả về Flux stream chứa các JSON chunk (STATUS, ANSWER_CHUNK, CLARIFICATION).
     */
    Flux<String> handleChat(String query, String conversationId, String userId, Map<String, String> headers);
}
