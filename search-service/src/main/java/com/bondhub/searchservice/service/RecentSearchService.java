package com.bondhub.searchservice.service;

import com.bondhub.searchservice.dto.request.RecentSearchRequest;
import com.bondhub.searchservice.dto.response.RecentHistoryResponse;
import com.bondhub.searchservice.dto.response.RecentSearchResponse;
import com.bondhub.searchservice.enums.SearchType;

import java.util.List;

public interface RecentSearchService {
    void addSearchItem(RecentSearchRequest request);
    List<RecentSearchResponse> getRecentItems();
    List<RecentSearchResponse> getRecentQueries();
    RecentHistoryResponse getRecentHistory();
    void removeItem(String itemId, SearchType type);
    void clearAllHistory();
}
