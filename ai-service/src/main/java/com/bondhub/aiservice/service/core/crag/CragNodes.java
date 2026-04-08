package com.bondhub.aiservice.service.core.crag;

import com.bondhub.aiservice.dto.CragState;
import com.bondhub.aiservice.service.ai.AnalyzerService;
import com.bondhub.aiservice.service.ai.GeneratorService;
import com.bondhub.aiservice.service.ai.GraderService;
import com.bondhub.aiservice.service.ai.QueryRewriterService;
import com.bondhub.aiservice.service.retrival.vectorsearch.VectorSearchService;
import com.bondhub.aiservice.service.retrival.websearch.WebSearchService;
import com.bondhub.aiservice.tools.ZaloAssistantTools;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class CragNodes {

    private final QueryRewriterService queryRewriter;
    private final AnalyzerService analyzer;
    private final VectorSearchService vectorSearch;
    private final GraderService grader;
    private final WebSearchService webSearch;
    private final GeneratorService generatorService;

    public Map<String, Object> rewriteNode(CragState state) {
        String historyStr = cleanHistoryToString(state.getHistory());
        String baseQuery;

        // Resume path: combine original query + previous clarification + latest user context
        if (state.isResumedFromCheckpoint()
                && state.getOriginalQuery() != null
                && state.getMissingFieldInfo() != null
                && !state.getMissingFieldInfo().isBlank()) {
            baseQuery = state.getOriginalQuery()
                    + ". Câu hỏi làm rõ: " + state.getMissingFieldInfo()
                    + ". Người dùng bổ sung: " + safe(state.getUserQuery());
        } else {
            baseQuery = safe(state.getUserQuery());
        }

        String rewritten = queryRewriter.rewrite(historyStr, baseQuery);
        if (rewritten == null || rewritten.isBlank()) {
            rewritten = baseQuery;
        }
        return Map.of("rewrittenQuery", rewritten);
    }

    public Map<String, Object> analyzeNode(CragState state) {
        String route = analyzer.analyzeAndRoute(safe(state.getRewrittenQuery()), safe(state.getCurrentTime()));
        String normalized = route == null ? "DIRECT" : route.trim();
        return Map.of("route", normalized);
    }

    public Map<String, Object> clarifyNode(CragState state) {
        String route = state.getRoute() == null ? "" : state.getRoute();
        String question = route.replaceFirst("(?i)^MISSING:\\s*", "").trim();
        return Map.of("missingFieldInfo", question);
    }

    public Map<String, Object> retrieveNode(CragState state) {
        List<String> docs = vectorSearch.search(safe(state.getRewrittenQuery()), state.getConversationId(), 10);
        String internal = docs == null || docs.isEmpty() ? "" : String.join("\n---\n", docs);
        return Map.of(
                "internalContext", internal,
                "context", internal
        );
    }

    public Map<String, Object> gradeNode(CragState state) {
        String prompt = "Q: " + safe(state.getRewrittenQuery()) + "\nCtx: " + safe(state.getContext());
        String grade = grader.grade(prompt);
        String normalized = grade == null ? "INCORRECT" : grade.trim().toUpperCase();
        return Map.of("grade", normalized);
    }

    public Map<String, Object> webSearchNode(CragState state) {
        String webData = webSearch.search(safe(state.getRewrittenQuery()), safe(state.getCurrentTime())).block();
        String safeWeb = webData == null ? "" : webData;

        String merged = safe(state.getInternalContext());
        if (!merged.isBlank()) {
            merged += "\n\nDữ liệu từ Internet:\n" + safeWeb;
        } else {
            merged = "Dữ liệu từ Internet:\n" + safeWeb;
        }

        return Map.of(
                "webContext", safeWeb,
                "context", merged,
                "retryCount", state.getRetryCount() + 1
        );
    }

    public Map<String, Object> markLowConfidenceNode(CragState state) {
        return Map.of(
                "lowConfidenceContext", true,
                "qualityNote", "DATA_MAY_BE_INCOMPLETE"
        );
    }

    // Streams answer tokens directly to FE sink while aggregating final answer for persistence.
    public Map<String, Object> generateNode(CragState state) {
        Sinks.Many<String> sink = GraphRuntimeRegistry.getSink(state.getConvId());
        if (sink == null) {
            throw new IllegalStateException("Missing sink for convId=" + state.getConvId());
        }

        StringBuilder full = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        String generationContext = safe(state.getContext());
        if (state.isLowConfidenceContext()) {
            generationContext = generationContext
                + "\n\nLưu ý nội bộ: dữ liệu có thể chưa đầy đủ, hãy trả lời hữu ích nhưng nêu rõ giới hạn thông tin một cách lịch sự.";
        }

        ZaloAssistantTools.registerConversation(state.getConvId(), state.getUserId());
        TokenStream stream = generatorService.generate(
                state.getConvId(),
            generationContext,
                safe(state.getUserQuery()),
                safe(state.getCurrentTime())
        );

        stream.onNext(token -> {
                    full.append(token);
                    sink.tryEmitNext("{\"type\":\"ANSWER_CHUNK\",\"content\":\"" + escapeJson(token) + "\"}");
                })
                .onComplete(response -> {
                    ZaloAssistantTools.unregisterConversation(state.getConvId());
                    latch.countDown();
                })
                .onError(err -> {
                    ZaloAssistantTools.unregisterConversation(state.getConvId());
                    errorRef.set(err);
                    latch.countDown();
                })
                .start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting token stream", e);
        }

        Throwable error = errorRef.get();
        if (error != null) {
            throw new RuntimeException(error);
        }

        String answer = full.toString();
        return Map.of(
                "finalAnswer", answer,
                "suggestedQuestions", parseSuggestions(answer)
        );
    }

    private static String cleanHistoryToString(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : history) {
            if (msg instanceof UserMessage um) {
                sb.append("User: ").append(safe(um.singleText())).append("\n");
            } else if (msg instanceof AiMessage am) {
                sb.append("AI: ").append(safe(am.text())).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static List<String> parseSuggestions(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        Pattern p = Pattern.compile("<suggestions>(.*?)</suggestions>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(answer);
        if (!m.find()) {
            return List.of();
        }
        return Arrays.stream(m.group(1).split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(3)
                .toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
