package com.bondhub.aiservice.controller;

import com.bondhub.aiservice.service.core.crag.AgenticCragService;
import com.bondhub.aiservice.service.ai.SmartReplyService;
import com.bondhub.aiservice.service.ai.SummarizationService;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.aiservice.util.HeaderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AssistantController {

    private final SmartReplyService smartReplyService;
    private final SummarizationService summarizationService;
    private final AgenticCragService agenticCragService;

    @PostMapping(value = "/chat/agentic", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agenticChat(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "anonymous") String userId,
            @RequestBody MessageSendRequest request,
            ServerHttpRequest httpRequest) {
        log.info("[Assistant] Received agentic chat request from user: {} for conversation: {}",
                userId, request.conversationId());

        Map<String, String> forwardHeaders = HeaderUtil.extractForwardHeaders(httpRequest);
        log.info("[Assistant] Forward headers captured: {}", forwardHeaders);

        return agenticCragService.handleChat(request.content(), request.conversationId(), userId, forwardHeaders);
    }

    @PostMapping("/smart-reply")
    public String getSmartReplies(@RequestBody List<String> messages) {
        return smartReplyService.generateReplies(messages);
    }

    @PostMapping("/summarize")
    public String getSummary(@RequestBody String text) {
        return summarizationService.summarize(text);
    }

}
