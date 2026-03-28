package com.bondhub.aiservice.controller;

import com.bondhub.aiservice.service.AgenticCragService;
import com.bondhub.aiservice.service.AiStreamService;
import com.bondhub.aiservice.service.SmartReplyService;
import com.bondhub.aiservice.service.SummarizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AssistantController {

    private final AiStreamService aiStreamService;
    private final SmartReplyService smartReplyService;
    private final SummarizationService summarizationService;
    private final AgenticCragService agenticCragService;

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestParam String userId,
            @RequestParam String chatId,
            @RequestParam String message) {
        return aiStreamService.streamChat(userId, chatId, message);
    }

    /**
     * Agentic CRAG endpoint: Analyzer → Retrieval → Grader → Generator
     * Yêu cầu header X-User-Id từ Gateway (hoặc manual khi test direct).
     */
    @PostMapping("/chat/agentic")
    public Mono<ResponseEntity<String>> agenticChat(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "anonymous") String userId,
            @RequestParam(defaultValue = "global") String conversationId,
            @RequestParam String message) {
        return Mono.fromCallable(() -> agenticCragService.handleChat(message, conversationId, userId))
                .map(ResponseEntity::ok)
                .subscribeOn(Schedulers.boundedElastic()); // tránh block Event Loop
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
