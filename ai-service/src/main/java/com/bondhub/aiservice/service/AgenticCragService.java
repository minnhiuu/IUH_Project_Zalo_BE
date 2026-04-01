package com.bondhub.aiservice.service;

import com.bondhub.aiservice.dto.AgentState;
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

    /**
     * Entry point cho luồng AI Chat (Reactive Stream).
     * Đã tích hợp Persistence MongoDB (qua Kafka) để lưu lịch sử chat vĩnh viễn.
     */
    public Flux<String> handleChat(String query, String conversationId, String userId) {
        log.info("[CRAG] Received query from user: {} in conversation: {}", userId, conversationId);
        String convId = conversationId + ":" + userId;
        AgentState state = agentStateService.get(convId);
        
        // Lấy thời gian thực từ Server (mỏ neo sự thật cho AI)
        String currentTime = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN")));

        // BƯỚC 1: Lưu câu hỏi của User vào Database (MongoDB) để không bị mất khi F5
        kafkaTemplate.send("ai.message.save", AiMessageSaveEvent.builder()
                .chatId(conversationId)
                .userId(userId)
                .senderId(userId) // Thăng này là User gửi
                .content(query)
                .build());

        return Mono.fromCallable(() -> {
            // LUỒNG A: Đang trong trạng thái chờ, kiểm tra xem user có "quay xe" không
            if (state != null && "WAIT_FOR_CONTEXT".equals(state.getCurrentState())) {
                log.info("[ROUTER] Checking intent switch for: {}", query);
                String decision = analyzerService.checkIntentSwitch(convId, state.getLastQuery(), query, currentTime);

                if ("NEW_INTENT".equals(decision)) {
                    log.info("[ROUTER] User switched topic. Resetting state.");
                    agentStateService.clear(convId);
                    return analyzeAndRouteNewQuery(query, convId, currentTime);
                }
                
                if ("CONTINUE".equals(decision)) {
                    String mergedQuery = "Câu hỏi gốc: " + state.getLastQuery() + ". Bổ sung: " + query;
                    log.info("[ROUTER] Continuing previous context for conv: {}", convId);
                    agentStateService.clear(convId);
                    return new RoutingResult("RAG", mergedQuery);
                }
                
                if (decision.startsWith("MISSING:")) return new RoutingResult("MISSING", decision);
            }

            // LUỒNG B: Xử lý như câu hỏi mới
            return analyzeAndRouteNewQuery(query, convId, currentTime);
        })
        .flatMapMany(result -> {
            // Luồng phản hồi trực tiếp (Direct/Chit-chat/Memory lookup)
            if ("DIRECT".equals(result.type())) {
                return streamResponse(result.query(), convId, conversationId, userId, currentTime);
            }
            
            // Luồng hỏi làm rõ thông tin
            if ("MISSING".equals(result.type())) {
                String msg = result.query().replace("MISSING:", "").trim();
                // Phải lưu tin nhắn làm rõ của AI vào DB
                kafkaTemplate.send("ai.message.save", AiMessageSaveEvent.builder()
                        .chatId(conversationId)
                        .userId(userId)
                        .senderId("ai-assistant-001")
                        .content(msg)
                        .build());
                return Flux.just(formatClarification(result.query(), convId));
            }
            
            // LUỒNG C: Chạy RAG/CRAG Pipeline cho các câu hỏi phức tạp
            return runCragPipeline(result.query(), convId, conversationId, userId, currentTime);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    private RoutingResult analyzeAndRouteNewQuery(String query, String convId, String currentTime) {
        String route = analyzerService.analyzeAndRoute(convId, query, currentTime);
        log.info("[ROUTER] Route decision: {}", route);
        if ("DIRECT".equals(route)) return new RoutingResult("DIRECT", query);
        if (route.startsWith("MISSING:")) return new RoutingResult("MISSING", route);
        return new RoutingResult("RAG", query);
    }

    private Flux<String> runCragPipeline(String query, String convId, String realConversationId, String userId, String currentTime) {
        return runPipelineLogic(query, convId) // Logic Retrieval + Grading + WebSearch
                .flatMapMany(prompt -> streamResponse(prompt, convId, realConversationId, userId, currentTime));
    }

    private Mono<String> runPipelineLogic(String query, String convId) {
        return Mono.fromCallable(() -> {
            log.info("[CRAG] Retrieving from vector store for: {}", query);
            List<String> contexts = vectorSearchService.search(query, 10);
            
            if (contexts.isEmpty()) return new GradingResult("INCORRECT", "");
            
            String internalContext = String.join("\n---\n", contexts);
            String gradeInput = "Q: " + query + "\nCtx: " + internalContext;
            
            // Sử dụng memoryId (convId) cho Grader để hiểu context của câu hỏi follow-up
            String grade = graderService.grade(convId, gradeInput).trim().toUpperCase();
            
            return new GradingResult(grade, internalContext);
        }).flatMap(res -> {
            // Nếu dữ liệu yếu -> Đi search Web (Tavily)
            if (!"CORRECT".equalsIgnoreCase(res.grade())) {
                log.warn("[CRAG] Internal data weak (grade={}). Web search fallback...", res.grade());
                return webSearchService.search(query).map(webData -> 
                    "Dữ liệu tổng hợp từ Internet (có thể chứa data cũ):\n" + webData + "\n\n" +
                    "Dữ liệu nội bộ (tham khảo):\n" + res.context() + "\n\n" +
                    "Câu hỏi: " + query);
            }
            // Nếu dữ liệu tốt -> Dùng dữ liệu nội bộ
            return Mono.just("Dữ liệu nội bộ:\n" + res.context() + "\n\nCâu hỏi: " + query); 
        });
    }

    private record GradingResult(String grade, String context) {}

    private Flux<String> streamResponse(String prompt, String convId, String realConversationId, String userId, String currentTime) {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        StringBuilder fullAnswer = new StringBuilder();

        // Sử dụng memoryId (convId) để Generator có lịch sử chat
        generatorService.generate(convId, prompt, currentTime)
                .onNext(token -> {
                    fullAnswer.append(token);
                    sink.tryEmitNext("{\"type\":\"ANSWER_CHUNK\",\"content\":\"" + escapeJson(token) + "\"}");
                })
                .onComplete(res -> {
                    log.info("[CRAG] Streaming complete for conv: {}. Saving to history...", convId);
                    if (realConversationId != null && userId != null) {
                        // BƯỚC 2: Lưu câu trả lời của AI vào Database (MongoDB)
                        kafkaTemplate.send("ai.message.save", AiMessageSaveEvent.builder()
                                .chatId(realConversationId)
                                .userId(userId)
                                .senderId("ai-assistant-001") // Thằng này là AI gửi
                                .content(fullAnswer.toString())
                                .build());
                    }
                    sink.tryEmitComplete();
                })
                .onError(err -> {
                    log.error("[CRAG] Streaming error: {}", err.getMessage());
                    sink.tryEmitError(err);
                })
                .start();
        return sink.asFlux();
    }

    private String formatClarification(String result, String convId) {
        String msg = result.replace("MISSING:", "").trim();
        agentStateService.save(convId, AgentState.builder()
                .conversationId(convId).lastQuery(msg).currentState("WAIT_FOR_CONTEXT").build());
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
