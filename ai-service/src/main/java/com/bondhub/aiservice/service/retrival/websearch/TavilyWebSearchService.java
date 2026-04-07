package com.bondhub.aiservice.service.retrival.websearch;

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
public class TavilyWebSearchService implements WebSearchService {

    private final WebClient webClient = WebClient.create();

    @Value("${tavily.api-key}")
    private String apiKey;

    @Override
    public Mono<String> search(String query, String currentTime) {
        String temporalTag = extractMonthYear(currentTime);
        String enrichedQuery = temporalTag.isEmpty() ? query : query + " " + temporalTag;

        log.info("[Tavily] Searching web for: {}", enrichedQuery);

        return webClient.post()
                .uri("https://api.tavily.com/search")
                .bodyValue(Map.of(
                        "api_key", apiKey,
                        "query", enrichedQuery,
                        "search_depth", "advanced",
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

    private String extractMonthYear(String currentTime) {
        if (currentTime == null || currentTime.isBlank()) return "";
        try {
            String[] parts = currentTime.split(",");
            if (parts.length < 2) return "";
            String datePart = parts[1].trim().split(" ")[0];
            String[] dateSplit = datePart.split("/");
            if (dateSplit.length < 3) return "";
            return "tháng " + dateSplit[1] + "/" + dateSplit[2];
        } catch (Exception e) {
            log.warn("[Tavily] Cannot parse currentTime for temporal tag: {}", currentTime);
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private String parseTavilyResponse(Map response) {
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results == null || results.isEmpty()) return "";

        return results.stream()
                .map(res -> String.format("- Source: %s\n  Content: %s", res.get("url"), res.get("content")))
                .collect(Collectors.joining("\n\n"));
    }
}
