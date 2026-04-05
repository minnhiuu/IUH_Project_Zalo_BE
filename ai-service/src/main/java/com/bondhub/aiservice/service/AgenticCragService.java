package com.bondhub.aiservice.service;

import com.bondhub.aiservice.dto.AgentState;
import com.bondhub.aiservice.dto.AiProcessingStatus;
import com.bondhub.aiservice.model.MongoChatMemoryStore;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class AgenticCragService {

    private final AnalyzerService analyzerService;
    private final GraderService graderService;
    private final GeneratorService generatorService;
    private final VectorSearchService vectorSearchService;
    private final AgentStateService agentStateService;
    private final TavilyWebSearchService webSearchService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final QueryRewriterService queryRewriterService;    // [Query Rewriter]
    private final MongoChatMemoryStore mongoChatMemoryStore;    // [Clean History]

    // ══════════════════════════════════════════════════════════════════════════
    // ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════════

    public Flux<String> handleChat(String query, String conversationId, String userId) {
        log.info("[CRAG] Received query from user: {} in conversation: {}", userId, conversationId);
        String convId = conversationId + ":" + userId;
        AgentState state = agentStateService.get(convId);

        String currentTime = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN")));

        // BƯỚC 1: Lưu câu hỏi của User vào DB qua Kafka
        kafkaTemplate.send("ai.message.save", AiMessageSaveEvent.builder()
                .chatId(conversationId)
                .userId(userId)
                .senderId(userId)
                .content(query)
                .build());

        // ── BƯỚC 0: REWRITE NGAY LẬP TỨC — trước khi Analyzer nhìn vào query ─────────
        // Mục đích: biến "giá xăng ron 95-III" thành "giá xăng ron 95-III tại HCM"
        // để Analyzer không trả MISSING vì thiếu địa điểm
        String processingQuery = query; // query gốc dùng để lưu Kafka/Chat
        if (state == null || !"WAIT_FOR_CONTEXT".equals(state.getCurrentState())) {
            // Chỉ rewrite khi không trong trạng thái chờ làm rõ (slot-filling)
            try {
                String cleanHistory = mongoChatMemoryStore.getCleanHistory(convId, 10);
                if (!cleanHistory.isBlank()) {
                    String rewritten = queryRewriterService.rewrite(cleanHistory, query);
                    if (rewritten != null && !rewritten.isBlank()) {
                        log.info("[QueryRewriter] '{}' -> '{}'", query, rewritten);
                        processingQuery = rewritten;
                    }
                }
            } catch (Exception e) {
                log.warn("[QueryRewriter] Rewrite failed at entry point, using original. Error: {}", e.getMessage());
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        final String finalProcessingQuery = processingQuery; // effectively final for lambda

        return Mono.fromCallable(() -> {
            // LUỒNG A: Trong trạng thái chờ làm rõ, kiểm tra user có "quay xe" không
            if (state != null && "WAIT_FOR_CONTEXT".equals(state.getCurrentState())) {
                log.info("[ROUTER] Checking intent switch for: {}", query);
                String decision = analyzerService.checkIntentSwitch(convId, state.getLastQuery(), query, currentTime);

                if ("NEW_INTENT".equals(decision)) {
                    log.info("[ROUTER] User switched topic. Resetting state.");
                    agentStateService.clear(convId);
                    return analyzeAndRouteNewQuery(finalProcessingQuery, convId, currentTime);
                }

                if ("CONTINUE".equals(decision)) {
                    String reconstructed = state.getOriginalQuery() != null
                            ? state.getOriginalQuery() + " " + query
                            : state.getLastQuery() + ". Bổ sung: " + query;
                    log.info("[ROUTER] Continuing previous context. Reconstructed: {}", reconstructed);
                    agentStateService.clear(convId);
                    return new RoutingResult("WEB_SEARCH", reconstructed);
                }

                if (decision.startsWith("MISSING:")) return new RoutingResult("MISSING", decision);
            }

            // LUỒNG B: Câu hỏi mới — dùng processingQuery (đã được enrich) cho Analyzer
            return analyzeAndRouteNewQuery(finalProcessingQuery, convId, currentTime);
        })
        .flatMapMany(result -> {
            if ("DIRECT".equals(result.type())) {
                // Phản hồi trực tiếp từ memory — không cần RAG, context rỗng
                Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
                emitStatus(sink, AiProcessingStatus.GENERATING_ANSWER);
                // Dùng original query cho DIRECT (câu hỏi về bản thân user không cần enrich)
                streamIntoSink(sink, "", query, convId, conversationId, userId, currentTime);
                return sink.asFlux();
            }

            if ("MISSING".equals(result.type())) {
                // Hỏi làm rõ thông tin
                String msg = result.query().replace("MISSING:", "").trim()
                        .replaceAll("(?i)^MISSING:\\s*", "");
                kafkaTemplate.send("ai.message.save", AiMessageSaveEvent.builder()
                        .chatId(conversationId).userId(userId).senderId("ai-assistant-001").content(msg).build());
                return Flux.just(formatClarification(result.query(), convId, query));
            }

            if ("WEB_SEARCH".equals(result.type())) {
                return runWebSearchPipeline(result.query(), convId, conversationId, userId, currentTime);
            }

            // RAG/CRAG Pipeline cho câu hỏi phức tạp
            return runCragPipeline(result.query(), convId, conversationId, userId, currentTime);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STATUS HELPER
    // ══════════════════════════════════════════════════════════════════════════

    /** Phát status event qua SSE sink để FE hiển thị trạng thái real-time */
    private void emitStatus(Sinks.Many<String> sink, AiProcessingStatus status) {
        sink.tryEmitNext("{\"type\":\"STATUS\",\"content\":\"" + status.name() + "\"}");
        log.debug("[CRAG] Emitting status: {}", status.name());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ROUTING
    // ══════════════════════════════════════════════════════════════════════════

    private RoutingResult analyzeAndRouteNewQuery(String query, String convId, String currentTime) {
        String route = analyzerService.analyzeAndRoute(convId, query, currentTime);
        log.info("[ROUTER] Route decision: {}", route);
        String normalized = route == null ? "" : route.trim();

        if ("DIRECT".equalsIgnoreCase(normalized)) return new RoutingResult("DIRECT", query);
        if (normalized.toUpperCase().startsWith("MISSING:")) return new RoutingResult("MISSING", normalized);
        if ("COMPLETE".equalsIgnoreCase(normalized)) return new RoutingResult("RAG", query);

        log.warn("[ROUTER] Unexpected route: '{}'. Defaulting to DIRECT.", normalized);
        return new RoutingResult("DIRECT", query);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PIPELINES
    // ══════════════════════════════════════════════════════════════════════════

    /** Web-only pipeline: gửi STATUS → WEB_SEARCHING rồi stream câu trả lời */
    private Flux<String> runWebSearchPipeline(String query, String convId, String realConversationId,
                                               String userId, String currentTime) {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        emitStatus(sink, AiProcessingStatus.WEB_SEARCHING);
        webSearchService.search(query, currentTime)
                .subscribe(
                        webData -> streamIntoSink(sink, "Dữ liệu từ Internet:\n" + webData, query, convId, realConversationId, userId, currentTime),
                        err -> { log.error("[WebSearch] error: {}", err.getMessage()); sink.tryEmitError(err); }
                );
        return sink.asFlux();
    }

    /** CRAG pipeline: RETRIEVING → (GRADING) → (WEB_SEARCHING) → GENERATING */
    private Flux<String> runCragPipeline(String query, String convId, String realConversationId,
                                          String userId, String currentTime) {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        emitStatus(sink, AiProcessingStatus.RETRIEVING_VECTOR);
        runPipelineLogic(query, convId, realConversationId, currentTime, sink)
                .subscribe(
                        context -> streamIntoSink(sink, context, query, convId, realConversationId, userId, currentTime),
                        err -> { log.error("[CRAG] Pipeline error: {}", err.getMessage()); sink.tryEmitError(err); }
                );
        return sink.asFlux();
    }

    /** Retrieval + Grading logic — query đã được rewrite từ handleChat, không cần rewrite lại */
    private Mono<String> runPipelineLogic(String query, String convId, String realConversationId,
                                           String currentTime, Sinks.Many<String> sink) {
        return Mono.fromCallable(() -> {
            // query ở đây đã là processingQuery (rewritten) từ handleChat
            log.info("[CRAG] Filtered vector search | conv: {} | searchQuery: {}", realConversationId, query);
            List<String> contexts = vectorSearchService.search(query, realConversationId, 10);

            if (contexts.isEmpty()) return "";

            emitStatus(sink, AiProcessingStatus.GRADING_DATA);
            String internalContext = String.join("\n---\n", contexts);
            // Grade dùng query đã rewrite (nhất quán với searchQuery)
            String gradeInput = "Q: " + query + "\nCtx: " + internalContext;
            String grade = graderService.grade(convId, gradeInput).trim().toUpperCase();

            return new GradingResult(grade, internalContext);
        }).flatMap(res -> {
            if (res instanceof String) {
                // Không có dữ liệu nội bộ → fallback web search
                log.warn("[CRAG] No internal context. Web search fallback...");
                emitStatus(sink, AiProcessingStatus.WEB_SEARCHING);
                return webSearchService.search(query, currentTime)
                        .map(webData -> "Dữ liệu từ Internet:\n" + webData);
            }
            GradingResult result = (GradingResult) res;
            if (!"CORRECT".equalsIgnoreCase(result.grade())) {
                log.warn("[CRAG] Internal data weak (grade={}). Web search fallback...", result.grade());
                emitStatus(sink, AiProcessingStatus.WEB_SEARCHING);
                return webSearchService.search(query, currentTime).map(webData ->
                    "Dữ liệu từ Internet:\n" + webData + "\n\nDữ liệu nội bộ (tham khảo):\n" + result.context());
            }
            return Mono.just("Dữ liệu nội bộ:\n" + result.context());
        });
    }

    private record GradingResult(String grade, String context) {}

    // ══════════════════════════════════════════════════════════════════════════
    // STREAMING CORE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Pump tokens từ GeneratorService vào sink đã có sẵn.
     * context và userQuery truyền riêng biệt để LangChain4j gộp đúng ChatMemory.
     */
    private void streamIntoSink(Sinks.Many<String> sink, String context, String userQuery,
                                 String convId, String realConversationId, String userId, String currentTime) {
        StringBuilder fullAnswer = new StringBuilder();
        emitStatus(sink, AiProcessingStatus.GENERATING_ANSWER);
        try {
            generatorService.generate(convId, context, userQuery, currentTime)
                    .onNext(token -> {
                        fullAnswer.append(token);
                        sink.tryEmitNext("{\"type\":\"ANSWER_CHUNK\",\"content\":\"" + escapeJson(token) + "\"}");
                    })
                    .onComplete(res -> {
                        log.info("[CRAG] Streaming complete for conv: {}. Saving to history...", convId);
                        if (realConversationId != null && userId != null) {
                            kafkaTemplate.send("ai.message.save", AiMessageSaveEvent.builder()
                                    .chatId(realConversationId)
                                    .userId(userId)
                                    .senderId("ai-assistant-001")
                                    .content(fullAnswer.toString())
                                    .build());
                        }
                        sink.tryEmitComplete();
                    })
                    .onError(err -> {
                        log.error("[CRAG] Streaming error: {}", err.getMessage());
                        if (err instanceof IllegalArgumentException && err.getMessage().contains("variable")) {
                            log.warn("[CRAG] Corrupted memory for conv: {}. Clearing...", convId);
                            agentStateService.clear(convId);
                            sink.tryEmitNext("{\"type\":\"ANSWER_CHUNK\",\"content\":\"Xin lỗi, phiên trò chuyện cũ bị lỗi và đã được làm mới. Vui lòng đặt lại câu hỏi.\"}");
                        }
                        sink.tryEmitError(err);
                    })
                    .start();
        } catch (IllegalArgumentException e) {
            log.warn("[CRAG] Prompt template mismatch for conv: {}. Clearing memory...", convId);
            agentStateService.clear(convId);
            sink.tryEmitNext("{\"type\":\"ANSWER_CHUNK\",\"content\":\"Xin lỗi, tôi gặp sự cố với lịch sử trò chuyện. Phiên chat đã được làm mới, bạn có thể đặt lại câu hỏi!\"}");
            sink.tryEmitComplete();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private String formatClarification(String result, String convId, String originalUserQuery) {
        String msg = result.replace("MISSING:", "").trim();
        agentStateService.save(convId, AgentState.builder()
                .conversationId(convId)
                .lastQuery(msg)
                .originalQuery(originalUserQuery)
                .currentState("WAIT_FOR_CONTEXT")
                .build());
        return "{\"type\":\"CLARIFICATION\",\"content\":\"" + escapeJson(msg) + "\"}";
    }

    private record RoutingResult(String type, String query) {}

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
