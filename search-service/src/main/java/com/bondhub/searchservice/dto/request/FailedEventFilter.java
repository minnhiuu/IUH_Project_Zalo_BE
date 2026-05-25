package com.bondhub.searchservice.dto.request;

import com.bondhub.searchservice.enums.SearchIndexType;
import lombok.Builder;

@Builder
public record FailedEventFilter(
    Boolean resolved,
    String keyword,
    Integer hours,
    SearchIndexType type,
    Integer page,
    Integer size
) {
    public int getPage() {
        return page != null ? page : 0;
    }

    public int getSize() {
        return size != null ? size : 10;
    }
}
