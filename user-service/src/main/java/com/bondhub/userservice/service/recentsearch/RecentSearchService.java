package com.bondhub.userservice.service.recentsearch;

import com.bondhub.userservice.dto.request.recentsearch.RecentSearchRequest;
import com.bondhub.userservice.dto.response.RecentSearchResponse;
import com.bondhub.userservice.model.enums.SearchType;

import java.util.List;

public interface RecentSearchService {
    void addSearchItem(RecentSearchRequest request);

    List<RecentSearchResponse> getRecentItems();

    List<RecentSearchResponse> getRecentQueries();

    void removeItem(String itemId, SearchType type);

    void clearAllHistory();
}
