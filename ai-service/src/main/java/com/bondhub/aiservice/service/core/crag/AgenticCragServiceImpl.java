package com.bondhub.aiservice.service.core.crag;

import com.bondhub.aiservice.dto.AgentState;
import com.bondhub.aiservice.model.enums.AiProcessingStatus;
import com.bondhub.aiservice.model.MongoChatMemoryStore;
import com.bondhub.aiservice.security.AiSecurityContextHolder;
import com.bondhub.aiservice.service.ai.AnalyzerService;
import com.bondhub.aiservice.service.ai.GeneratorService;
import com.bondhub.aiservice.service.ai.GraderService;
import com.bondhub.aiservice.service.ai.QueryRewriterService;
import com.bondhub.aiservice.service.core.state.AgentStateService;
import com.bondhub.aiservice.service.retrival.websearch.WebSearchService;
import com.bondhub.aiservice.service.retrival.vectorsearch.VectorSearchService;
import com.bondhub.common.event.ai.AiMessageSaveEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

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

    private final AnalyzerService analyzerService;
    private final GraderService graderService;
    private final GeneratorService generatorService;
    private final VectorSearchService vectorSearchService;
    private final AgentStateService agentStateService;
    private final WebSearchService webSearchService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final QueryRewriterService queryRewriterService;
    private final MongoChatMemoryStore mongoChatMemoryStore;

    private static final Set<String> SOCIAL_GREETINGS = Set.of(
            "hi", "hello", "hey", "chào", "xin chào", "alo", "ê", "ơi"
    );

    @Override
    public Flux<String> handleChat(String query, String conversationId, String userId, Map<String, String> headers) {
        log.info("[CRAG] Received query from user: {} in conversation: {}", userId, conversationId);
        String convId = conversationId + ":" + userId;
        AgentState state = agentStateService.get(convId);
        String currentTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN")));

        if (headers != null) {
            AiSecurityContextHolder.bind(convId, headers);
        }

        saveMessageToDb(conversationId, userId, userId, query);

        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

        processPipeline(query, convId, conversationId, userId, currentTime, state, sink)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> {
                    log.error("[CRAG] Unhandled pipeline error: {}", err.getMessage());
                    sink.tryEmitError(err);
                })
                .subscribe();

        return sink.asFlux()
                .doFinally(signal -> AiSecurityContextHolder.unbind(convId));
    }

    private Mono<Void> processPipeline(String query, String convId, String conversationId,
                                        String userId, String currentTime,
                                        AgentState state, Sinks.Many<String> sink) {
        return Mono.defer(() -> {

            if (isSocialGreeting(query)) {
                log.info("[FILTER] Social greeting detected: '{}'", query);
                return streamResponse(sink, "", query, convId, conversationId, userId, currentTime);
            }

            if (state != null && "WAIT_FOR_CONTEXT".equals(state.getCurrentState())) {
                log.info("[ROUTER] In WAIT_FOR_CONTEXT state. Checking intent switch...");
                String decision = analyzerService.checkIntentSwitch(state.getLastQuery(), query, currentTime);

                if ("NEW_INTENT".equals(decision)) {
                    log.info("[ROUTER] User switched topic. Resetting state.");
                    agentStateService.clear(convId);
                } else if ("CONTINUE".equals(decision)) {
                    String reconstructed = state.getOriginalQuery() != null
                            ? state.getOriginalQuery() + " " + query
                            : state.getLastQuery() + ". Bổ sung: " + query;
                    log.info("[ROUTER] Continuing slot-filling. Reconstructed: {}", reconstructed);
                    agentStateService.clear(convId);
                    return runWebSearchResult(reconstructed, convId, conversationId, userId, currentTime, sink);
                } else if (decision != null && decision.startsWith("MISSING:")) {
                    return emitMissing(decision, convId, conversationId, userId, query, sink);
                }
            }

            emitStatus(sink, AiProcessingStatus.ANALYZING_INTENT);

            String processingQuery = rewriteIfNeeded(query, convId);
            log.info("[Rewriter] '{}' -> '{}'", query, processingQuery);

            String route = analyzerService.analyzeAndRoute(processingQuery, currentTime);
            log.info("[ROUTER] Route: {}", route);
            String normalized = route == null ? "" : route.trim();

            if ("DIRECT".equalsIgnoreCase(normalized)) {
                return streamResponse(sink, "", query, convId, conversationId, userId, currentTime);
            }

            if (normalized.toUpperCase().startsWith("MISSING:")) {
                return emitMissing(normalized, convId, conversationId, userId, query, sink);
            }

            if ("COMPLETE".equalsIgnoreCase(normalized)) {
                agentStateService.save(convId, AgentState.builder()
                        .conversationId(convId)
                        .originalQuery(processingQuery)
                        .currentState("COMPLETED")
                        .build());
                return runCragResult(processingQuery, convId, conversationId, userId, currentTime, sink);
            }

            log.warn("[ROUTER] Unexpected route: '{}'. Defaulting to DIRECT.", normalized);
            return streamResponse(sink, "", query, convId, conversationId, userId, currentTime);
        });
    }

    private boolean isSocialGreeting(String query) {
        return SOCIAL_GREETINGS.contains(query.toLowerCase().trim());
    }

    private String rewriteIfNeeded(String query, String convId) {
        try {
            String cleanHistory = mongoChatMemoryStore.getCleanHistory(convId, 6);
            if (!cleanHistory.isBlank()) {
                String rewritten = queryRewriterService.rewrite(cleanHistory, query);
                if (rewritten != null && !rewritten.isBlank()) return rewritten;
            }
        } catch (Exception e) {
            log.warn("[QueryRewriter] Failed, using original. Error: {}", e.getMessage());
        }
        return query;
    }

    private Mono<Void> runCragResult(String query, String convId, String realConversationId,
                                      String userId, String currentTime, Sinks.Many<String> sink) {
        emitStatus(sink, AiProcessingStatus.RETRIEVING_VECTOR);
        return buildContext(query, convId, realConversationId, currentTime, sink)
                .flatMap(context -> streamResponse(sink, context, query, convId, realConversationId, userId, currentTime));
    }

    private Mono<Void> runWebSearchResult(String query, String convId, String realConversationId,
                                           String userId, String currentTime, Sinks.Many<String> sink) {
        emitStatus(sink, AiProcessingStatus.WEB_SEARCHING);
        return webSearchService.search(query, currentTime)
                .flatMap(webData -> streamResponse(sink, "Dữ liệu từ Internet:\n" + webData,
                        query, convId, realConversationId, userId, currentTime));
    }

    private Mono<String> buildContext(String query, String convId, String realConversationId,
                                       String currentTime, Sinks.Many<String> sink) {
        return Mono.fromCallable(() -> {
            log.info("[CRAG] Vector search | conv: {} | query: {}", realConversationId, query);
            List<String> contexts = vectorSearchService.search(query, realConversationId, 10);
            if (contexts.isEmpty()) return "";

            emitStatus(sink, AiProcessingStatus.GRADING_DATA);
            String internalContext = String.join("\n---\n", contexts);
            String grade = graderService.grade("Q: " + query + "\nCtx: " + internalContext)
                                        .trim().toUpperCase();
            return new GradingResult(grade, internalContext);
        }).flatMap(res -> {
            if (res instanceof String) {
                log.warn("[CRAG] No internal context. Web fallback...");
                emitStatus(sink, AiProcessingStatus.WEB_SEARCHING);
                return webSearchService.search(query, currentTime)
                        .map(webData -> "Dữ liệu từ Internet:\n" + webData);
            }
            GradingResult result = (GradingResult) res;
            if (!"CORRECT".equalsIgnoreCase(result.grade())) {
                log.warn("[CRAG] Weak internal data ({}). Web fallback...", result.grade());
                emitStatus(sink, AiProcessingStatus.WEB_SEARCHING);
                return webSearchService.search(query, currentTime)
                        .map(webData -> "Dữ liệu từ Internet:\n" + webData
                                + "\n\nDữ liệu nội bộ (tham khảo):\n" + result.context());
            }
            return Mono.just("Dữ liệu nội bộ:\n" + result.context());
        });
    }

    private record GradingResult(String grade, String context) {}

    private Mono<Void> streamResponse(Sinks.Many<String> sink, String context, String userQuery,
                                       String convId, String realConversationId, String userId, String currentTime) {
        return Mono.create(monoSink -> {
            emitStatus(sink, AiProcessingStatus.GENERATING_ANSWER);
            StringBuilder fullAnswer = new StringBuilder();
            try {
                com.bondhub.aiservice.tools.ZaloAssistantTools.registerConversation(convId, userId);

                generatorService.generate(convId, context, userQuery, currentTime)
                        .onNext(token -> {
                            fullAnswer.append(token);
                            sink.tryEmitNext("{\"type\":\"ANSWER_CHUNK\",\"content\":\"" + escapeJson(token) + "\"}");
                        })
                        .onComplete(res -> {
                            log.info("[CRAG] Stream complete | conv: {}", convId);
                            com.bondhub.aiservice.tools.ZaloAssistantTools.unregisterConversation(convId);
                            saveMessageToDb(realConversationId, userId, "ai-assistant-001", fullAnswer.toString());
                            sink.tryEmitComplete();
                            monoSink.success();
                        })
                        .onError(err -> {
                            log.error("[CRAG] Stream error: {}", err.getMessage());
                            com.bondhub.aiservice.tools.ZaloAssistantTools.unregisterConversation(convId);
                            if (err instanceof IllegalArgumentException && err.getMessage() != null
                                    && err.getMessage().contains("variable")) {
                                log.warn("[CRAG] Corrupted memory for conv: {}. Clearing...", convId);
                                agentStateService.clear(convId);
                                sink.tryEmitNext("{\"type\":\"ANSWER_CHUNK\",\"content\":\"Xin lỗi, phiên trò chuyện bị lỗi và đã được làm mới. Vui lòng đặt lại câu hỏi.\"}");
                                sink.tryEmitComplete();
                                monoSink.success();
                            } else {
                                sink.tryEmitError(err);
                                monoSink.error(err);
                            }
                        })
                        .start();
            } catch (IllegalArgumentException e) {
                com.bondhub.aiservice.tools.ZaloAssistantTools.unregisterConversation(convId);
                log.warn("[CRAG] Prompt mismatch | conv: {}. Clearing memory...", convId);
                agentStateService.clear(convId);
                sink.tryEmitNext("{\"type\":\"ANSWER_CHUNK\",\"content\":\"Xin lỗi, tôi gặp sự cố với lịch sử trò chuyện. Phiên chat đã được làm mới!\"}");
                sink.tryEmitComplete();
                monoSink.success();
            }
        });
    }

    private Mono<Void> emitMissing(String missingResult, String convId, String conversationId,
                                    String userId, String originalQuery, Sinks.Many<String> sink) {
        return Mono.fromRunnable(() -> {
            String questionText = missingResult.replaceAll("(?i)^MISSING:\\s*", "").trim();
            // Wrap trong <question> tag để FE có thể nhận dạng khi load lại từ DB
            String persistContent = "<question>" + questionText + "</question>";
            saveMessageToDb(conversationId, userId, "ai-assistant-001", persistContent);
            agentStateService.save(convId, AgentState.builder()
                    .conversationId(convId)
                    .lastQuery(questionText)
                    .originalQuery(originalQuery)
                    .currentState("WAIT_FOR_CONTEXT")
                    .build());
            sink.tryEmitNext("{\"type\":\"CLARIFICATION\",\"content\":\"" + escapeJson(questionText) + "\"}");
            sink.tryEmitComplete();
        });
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
