package com.bondhub.aiservice.service.retrival.websearch;

import reactor.core.publisher.Mono;

public interface WebSearchService {

    Mono<String> search(String query, String currentTime);
}
