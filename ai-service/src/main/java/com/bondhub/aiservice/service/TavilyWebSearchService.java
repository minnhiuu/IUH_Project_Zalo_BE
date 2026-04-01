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

    public Mono<String> search(String query) {
        log.info("[Tavily] Searching web for: {}", query);

        return webClient.post()
                .uri("https://api.tavily.com/search")
                .bodyValue(Map.of(
                        "api_key", apiKey,
                        "query", query,
                        "search_depth", "basic",
                        "include_answer", false,
                        "max_results", 3
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::parseTavilyResponse)
                .onErrorResume(e -> {
                    log.error("[Tavily] Search failed: {}", e.getMessage());
                    return Mono.just("(Không thể kết nối Internet để tìm kiếm bổ sung)");
                });
    }

    private String parseTavilyResponse(Map response) {
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results == null || results.isEmpty()) return "";

        return results.stream()
                .map(res -> String.format("- Source: %s\n  Content: %s", res.get("url"), res.get("content")))
                .collect(Collectors.joining("\n\n"));
    }
}
