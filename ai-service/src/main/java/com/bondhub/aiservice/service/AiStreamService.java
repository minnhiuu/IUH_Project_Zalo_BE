package com.bondhub.aiservice.service;

import com.bondhub.aiservice.config.BondHubAssistant;
import com.bondhub.common.event.ai.AiMessageSaveEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiStreamService {
    private final BondHubAssistant assistant;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Flux<String> streamChat(String userId, String chatId, String userMessage) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder fullContent = new StringBuilder();

        log.info("Starting AI stream for user: {} in chat: {}", userId, chatId);

        assistant.chat(userId, userMessage)
                .onNext(token -> {
                    fullContent.append(token);
                    sink.tryEmitNext(token);
                })
                .onComplete(response -> {
                    log.info("AI stream completed for chat: {}. Publishing save event.", chatId);
                    kafkaTemplate.send("ai.message.save", AiMessageSaveEvent.builder()
                            .userId(userId)
                            .chatId(chatId)
                            .content(fullContent.toString())
                            .build());
                    sink.tryEmitComplete();
                })
                .onError(err -> {
                    log.error("AI stream error for chat: {}", chatId, err);
                    sink.tryEmitError(err);
                })
                .start();

        return sink.asFlux();
    }
}
