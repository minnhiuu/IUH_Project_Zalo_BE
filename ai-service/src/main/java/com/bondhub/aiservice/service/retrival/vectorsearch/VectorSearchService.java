package com.bondhub.aiservice.service.retrival.vectorsearch;

import java.util.List;

public interface VectorSearchService {

    List<String> search(String query, String conversationId, int topK);
}
