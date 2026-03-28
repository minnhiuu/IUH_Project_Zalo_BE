package com.bondhub.aiservice.service;

import com.bondhub.aiservice.dto.AgentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgenticCragService {

    private final AnalyzerService analyzerService;
    private final GraderService graderService;
    private final GeneratorService generatorService;
    private final VectorSearchService vectorSearchService;
    private final AgentStateService agentStateService;

    /**
     * Luồng CRAG Agentic: Analyzer → Retrieval → Grader → Generator (streaming)
     *
     * Trả về Flux<String> với 2 loại event JSON:
     *  - {"type":"CLARIFICATION","content":"..."} — yêu cầu user bổ sung thông tin
     *  - {"type":"ANSWER_CHUNK","content":"..."}  — từng token câu trả lời cuối
     */
    public Flux<String> handleChat(String query, String conversationId, String userId) {
        String convId = conversationId + ":" + userId;

        // Bước 1: Kiểm tra trạng thái cũ (đang chờ context bổ sung?)
        AgentState state = agentStateService.get(convId);
        String currentQuery = (state != null && "WAIT_FOR_CONTEXT".equals(state.getCurrentState()))
                ? "Câu hỏi gốc: " + state.getLastQuery() + ". Bổ sung thông tin: " + query
                : query;

        if (state != null && "WAIT_FOR_CONTEXT".equals(state.getCurrentState())) {
            log.info("[CRAG] Merging context from previous state for conv: {}", convId);
            agentStateService.clear(convId);
        }

        // Toàn bộ pipeline chạy trên boundedElastic để không block I/O
        return Mono.fromCallable(() -> runPipeline(currentQuery, convId))
                .flatMapMany(pipelineResult -> {
                    if (pipelineResult.needsClarification()) {
                        // Trả về yêu cầu làm rõ dưới dạng SSE JSON
                        String json = "{\"type\":\"CLARIFICATION\",\"content\":\""
                                + escapeJson(pipelineResult.clarification()) + "\"}";
                        return Flux.just(json);
                    }

                    // Stream từng token câu trả lời
                    Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

                    generatorService.generate(pipelineResult.prompt())
                            .onNext(token -> {
                                String json = "{\"type\":\"ANSWER_CHUNK\",\"content\":\""
                                        + escapeJson(token) + "\"}";
                                sink.tryEmitNext(json);
                            })
                            .onComplete(response -> {
                                log.info("[CRAG] Streaming complete for conv: {}", convId);
                                sink.tryEmitComplete();
                            })
                            .onError(err -> {
                                log.error("[CRAG] Streaming error for conv: {}", convId, err);
                                sink.tryEmitError(err);
                            })
                            .start();

                    return sink.asFlux();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ======================================================
    // Pipeline nội bộ (blocking — chạy trên boundedElastic)
    // ======================================================

    private PipelineResult runPipeline(String currentQuery, String convId) {
        // Bước 2: Analyzer — Slot Filling
        log.info("[CRAG] Analyzing query: '{}'", currentQuery);
        String analysisResult = analyzerService.analyze(currentQuery);

        if (analysisResult.startsWith("MISSING:")) {
            String clarification = analysisResult.replace("MISSING:", "").trim();
            agentStateService.save(convId, AgentState.builder()
                    .conversationId(convId)
                    .lastQuery(currentQuery)
                    .currentState("WAIT_FOR_CONTEXT")
                    .build());
            log.info("[CRAG] Query incomplete, asking: {}", clarification);
            return PipelineResult.clarification(clarification);
        }

        // Bước 3: Retrieval — Tìm trong Qdrant
        log.info("[CRAG] Retrieving from vector store...");
        List<String> contexts = vectorSearchService.search(currentQuery, 10);
        log.info("[CRAG] Retrieved {} context chunks", contexts.size());

        // Bước 4: Grader — Chấm điểm RAG
        String grade;
        if (contexts.isEmpty()) {
            grade = "INCORRECT";
            log.warn("[CRAG] No contexts retrieved, grading as INCORRECT");
        } else {
            String gradeInput = "Câu hỏi: " + currentQuery + "\n\nContext:\n" + String.join("\n---\n", contexts);
            grade = graderService.grade(gradeInput).trim().toUpperCase();
            log.info("[CRAG] Grade result: {}", grade);
        }

        // Bước 5: Web Search Fallback (placeholder)
        String finalContext = String.join("\n\n", contexts);
        if (!"CORRECT".equals(grade)) {
            log.warn("[CRAG] Internal data insufficient (grade={}). Web search fallback placeholder.", grade);
            // TODO: Tích hợp Tavily Web Search
            if (finalContext.isBlank()) {
                finalContext = "(Không có dữ liệu nội bộ liên quan)";
            }
        }

        // Bước 6: Chuẩn bị prompt cho Generator
        String prompt = "Nguồn dữ liệu:\n" + finalContext + "\n\nCâu hỏi của người dùng: " + currentQuery;
        return PipelineResult.answer(prompt);
    }

    // ======================================================
    // Internal Result DTO
    // ======================================================

    private record PipelineResult(boolean needsClarification, String clarification, String prompt) {
        static PipelineResult clarification(String msg) {
            return new PipelineResult(true, msg, null);
        }

        static PipelineResult answer(String prompt) {
            return new PipelineResult(false, null, prompt);
        }
    }

    // Escape JSON string value to avoid breaking SSE
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
