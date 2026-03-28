package com.bondhub.aiservice.service;

import com.bondhub.aiservice.dto.AgentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public String handleChat(String query, String conversationId, String userId) {
        String convId = conversationId + ":" + userId;
        AgentState state = agentStateService.get(convId);

        String currentQuery = query;

        // === Bước 1: Nếu đang chờ context bổ sung từ user ===
        if (state != null && "WAIT_FOR_CONTEXT".equals(state.getCurrentState())) {
            log.info("[CRAG] Merging context from previous state for conv: {}", convId);
            currentQuery = "Câu hỏi gốc: " + state.getLastQuery() + ". Bổ sung thông tin: " + query;
            agentStateService.clear(convId);
        }

        // === Bước 2: Analyzer — Slot Filling ===
        log.info("[CRAG] Analyzing query: '{}'", currentQuery);
        String analysisResult = analyzerService.analyze(currentQuery);

        if (analysisResult.startsWith("MISSING:")) {
            String clarification = analysisResult.replace("MISSING:", "").trim();
            agentStateService.save(convId, AgentState.builder()
                    .conversationId(convId)
                    .lastQuery(currentQuery)
                    .currentState("WAIT_FOR_CONTEXT")
                    .build());
            log.info("[CRAG] Query incomplete, asking for clarification: {}", clarification);
            return clarification;
        }

        // === Bước 3: Retrieval — Tìm trong Qdrant ===
        log.info("[CRAG] Retrieving from vector store...");
        List<String> contexts = vectorSearchService.search(currentQuery, 10);
        log.info("[CRAG] Retrieved {} context chunks", contexts.size());

        // === Bước 4: Grader — Chấm điểm kết quả RAG ===
        String grade;
        if (contexts.isEmpty()) {
            grade = "INCORRECT";
            log.warn("[CRAG] No contexts retrieved, grading as INCORRECT");
        } else {
            String gradeInput = "Câu hỏi: " + currentQuery + "\n\nContext:\n" + String.join("\n---\n", contexts);
            grade = graderService.grade(gradeInput).trim().toUpperCase();
            log.info("[CRAG] Grade result: {}", grade);
        }

        // === Bước 5: Web Search Fallback ===
        String finalContext = String.join("\n\n", contexts);
        if (!"CORRECT".equals(grade)) {
            log.warn("[CRAG] Internal data insufficient (grade={}). Web search fallback placeholder.", grade);
            // TODO: Tích hợp Tavily Web Search khi cần
            // String webData = webSearchService.search(currentQuery);
            // finalContext += "\n\n[Dữ liệu từ Internet]:\n" + webData;
            if (finalContext.isBlank()) {
                finalContext = "(Không có dữ liệu nội bộ liên quan)";
            }
        }

        // === Bước 6: Generator — Tổng hợp câu trả lời ===
        log.info("[CRAG] Generating final answer...");
        String prompt = "Nguồn dữ liệu:\n" + finalContext + "\n\nCâu hỏi của người dùng: " + currentQuery;
        return generatorService.generate(prompt);
    }
}
