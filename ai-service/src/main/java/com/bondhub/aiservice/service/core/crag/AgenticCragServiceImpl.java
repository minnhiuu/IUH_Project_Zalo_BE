package com.bondhub.aiservice.service.core.crag;

import com.bondhub.aiservice.dto.CragState;
import com.bondhub.aiservice.model.enums.AiProcessingStatus;
import com.bondhub.aiservice.model.MongoChatMemoryStore;
import com.bondhub.aiservice.security.AiSecurityContextHolder;
import com.bondhub.aiservice.service.ai.GeneratorService;
import com.bondhub.common.event.ai.AiMessageSaveEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import dev.langchain4j.data.message.ChatMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

//hoanghuy04
@Service
@Slf4j
@RequiredArgsConstructor
public class AgenticCragServiceImpl implements AgenticCragService {

    private final GeneratorService generatorService;
    private final CragGraphExecutor cragGraphExecutor;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MongoChatMemoryStore mongoChatMemoryStore;

    private static final Set<String> SOCIAL_GREETINGS = Set.of(
            "hi", "hello", "hey", "chào", "xin chào", "alo", "ê", "ơi"
    );

    @Override
    public Flux<String> handleChat(String query, String conversationId, String userId, Map<String, String> headers) {
        log.info("[Graph-Exec] Received query from user: {} in conversation: {}", userId, conversationId);
        String convId = conversationId + ":" + userId;
        String currentTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN")));

        if (headers != null) {
            AiSecurityContextHolder.bind(convId, headers);
        }

        saveMessageToDb(conversationId, userId, userId, query);

        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        GraphRuntimeRegistry.registerSink(convId, sink);

        if (isSocialGreeting(query)) {
            runGreetingFlow(sink, convId, conversationId, userId, query, currentTime);
            return sink.asFlux().doFinally(signal -> {
                AiSecurityContextHolder.unbind(convId);
                GraphRuntimeRegistry.unregisterSink(convId);
            });
        }

        List<ChatMessage> history = mongoChatMemoryStore.getMessages(convId);
        CragState initialState = CragState.builder()
                .userQuery(query)
                .originalQuery(query)
                .conversationId(conversationId)
                .userId(userId)
                .convId(convId)
                .currentTime(currentTime)
                .headers(headers == null ? Map.of() : headers)
                .history(history)
                .build();

        CragState resumedState = cragGraphExecutor.hydrateForResume(initialState);

        Mono.fromCallable(() -> cragGraphExecutor.execute(resumedState))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(finalState -> processFinalResult(finalState, sink))
                .doOnError(err -> {
                    log.error("[Graph-Exec] Error: {}", err.getMessage());
                    sink.tryEmitError(err);
                })
                .subscribe();

        return sink.asFlux().doFinally(signal -> {
            AiSecurityContextHolder.unbind(convId);
            GraphRuntimeRegistry.unregisterSink(convId);
        });
    }

    private void runGreetingFlow(Sinks.Many<String> sink,
                                 String convId,
                                 String conversationId,
                                 String userId,
                                 String query,
                                 String currentTime) {
        emitStatus(sink, AiProcessingStatus.GENERATING_ANSWER);
        StringBuilder fullAnswer = new StringBuilder();

        com.bondhub.aiservice.tools.ZaloAssistantTools.registerConversation(convId, userId);
        generatorService.generate(convId, "", query, currentTime)
                .onNext(token -> {
                    fullAnswer.append(token);
                    sink.tryEmitNext("{\"type\":\"ANSWER_CHUNK\",\"content\":\"" + escapeJson(token) + "\"}");
                })
                .onComplete(res -> {
                    com.bondhub.aiservice.tools.ZaloAssistantTools.unregisterConversation(convId);
                    saveMessageToDb(conversationId, userId, "ai-assistant-001", fullAnswer.toString());
                    sink.tryEmitComplete();
                })
                .onError(err -> {
                    com.bondhub.aiservice.tools.ZaloAssistantTools.unregisterConversation(convId);
                    sink.tryEmitError(err);
                })
                .start();
    }

    private boolean isSocialGreeting(String query) {
        return SOCIAL_GREETINGS.contains(query.toLowerCase().trim());
    }

    private void processFinalResult(CragState finalState, Sinks.Many<String> sink) {
        if (finalState.getRoute() != null && finalState.getRoute().toUpperCase().startsWith("MISSING:")) {
            String missing = finalState.getMissingFieldInfo();
            if (missing != null && !missing.isBlank()) {
                saveMessageToDb(finalState.getConversationId(), finalState.getUserId(), "ai-assistant-001", "<question>" + missing + "</question>");
                sink.tryEmitNext("{\"type\":\"CLARIFICATION\",\"content\":\"" + escapeJson(missing) + "\"}");
            }
            sink.tryEmitComplete();
            return;
        }

        if (finalState.getFinalAnswer() != null && !finalState.getFinalAnswer().isBlank()) {
            saveMessageToDb(finalState.getConversationId(), finalState.getUserId(), "ai-assistant-001", finalState.getFinalAnswer());
        }

        if (finalState.getSuggestedQuestions() != null && !finalState.getSuggestedQuestions().isEmpty()) {
            String payload = String.join("|", finalState.getSuggestedQuestions());
            sink.tryEmitNext("{\"type\":\"SUGGESTIONS\",\"content\":\"" + escapeJson(payload) + "\"}");
        }

        sink.tryEmitComplete();
    }

    private void emitStatus(Sinks.Many<String> sink, AiProcessingStatus status) {
        sink.tryEmitNext("{\"type\":\"STATUS\",\"content\":\"" + status.name() + "\"}");
        log.debug("[CRAG] Status: {}", status.name());
    }

    private void saveMessageToDb(String chatId, String userId, String senderId, String content) {
        if (chatId == null || content == null || content.isBlank()) return;
        kafkaTemplate.send("ai.message.save", AiMessageSaveEvent.builder()
                .chatId(chatId)
                .userId(userId)
                .senderId(senderId)
                .content(content)
                .build());
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
