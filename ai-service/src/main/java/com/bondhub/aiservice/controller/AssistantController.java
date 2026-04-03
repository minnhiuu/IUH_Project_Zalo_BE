package com.bondhub.aiservice.controller;

import com.bondhub.aiservice.service.AgenticCragService;
import com.bondhub.aiservice.service.SmartReplyService;
import com.bondhub.aiservice.service.SummarizationService;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

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
            @RequestBody MessageSendRequest request) {
        log.info("[Assistant] Received agentic chat request from user: {} for conversation: {}", 
                userId, request.recipientId());
        return agenticCragService.handleChat(request.content(), request.recipientId(), userId);
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
