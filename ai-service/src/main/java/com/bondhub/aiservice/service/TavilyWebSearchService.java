package com.bondhub.aiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TavilyWebSearchService {

    private final WebClient webClient = WebClient.create();

    @Value("${tavily.api-key}")
    private String apiKey;

    /**
     * Tìm kiếm web với Temporal Anchoring: đính kèm tháng/năm hiện tại vào query
     * để Tavily ưu tiên kết quả mới nhất, tránh trả về dữ liệu cũ.
     *
     * @param query       Câu hỏi gốc của user
     * @param currentTime Thời gian hiện tại từ server (dạng 'Thứ X, dd/MM/yyyy HH:mm')
     */
    public Mono<String> search(String query, String currentTime) {
        // Trích xuất tháng/năm từ currentTime để gắn vào query
        // currentTime có format: "Thứ Sáu, 04/04/2026 14:55" → lấy "04/2026"
        String temporalTag = extractMonthYear(currentTime);
        String enrichedQuery = temporalTag.isEmpty() ? query : query + " " + temporalTag;

        log.info("[Tavily] Searching web for: {}", enrichedQuery);

        return webClient.post()
                .uri("https://api.tavily.com/search")
                .bodyValue(Map.of(
                        "api_key", apiKey,
                        "query", enrichedQuery,
                        "search_depth", "advanced",   // deep scan — truy cập nội dung bên trong trang
                        "include_answer", false,
                        "max_results", 5
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::parseTavilyResponse)
                .onErrorResume(e -> {
                    log.error("[Tavily] Search failed: {}", e.getMessage());
                    return Mono.just("(Không thể kết nối Internet để tìm kiếm bổ sung)");
                });
    }

    /**
     * Trích xuạt tháng/năm từ chuỗi thời gian server.
     * Ví dụ: "Thứ Sáu, 04/04/2026 14:55" → "tháng 04/2026"
     */
    private String extractMonthYear(String currentTime) {
        if (currentTime == null || currentTime.isBlank()) return "";
        try {
            // Format: "Thứ X, dd/MM/yyyy HH:mm"
            // Tìm phần dd/MM/yyyy và lấy MM/yyyy
            String[] parts = currentTime.split(",");
            if (parts.length < 2) return "";
            String datePart = parts[1].trim().split(" ")[0]; // "04/04/2026"
            String[] dateSplit = datePart.split("/");
            if (dateSplit.length < 3) return "";
            return "tháng " + dateSplit[1] + "/" + dateSplit[2]; // "tháng 04/2026"
        } catch (Exception e) {
            log.warn("[Tavily] Cannot parse currentTime for temporal tag: {}", currentTime);
            return "";
        }
    }

    private String parseTavilyResponse(Map response) {
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results == null || results.isEmpty()) return "";

        return results.stream()
                .map(res -> String.format("- Source: %s\n  Content: %s", res.get("url"), res.get("content")))
                .collect(Collectors.joining("\n\n"));
    }
}
