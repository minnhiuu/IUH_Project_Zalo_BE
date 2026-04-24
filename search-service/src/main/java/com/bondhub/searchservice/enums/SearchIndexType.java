package com.bondhub.searchservice.enums;

import java.util.List;

public enum SearchIndexType {
    USER(List.of("user.search.index-requested", "user.search.index-requested.dlq", "user.search.index-deleted", "user.search.index-deleted.dlq")),
    MESSAGE(List.of("message.search.index-requested", "message.search.index-requested.dlq"));

    private final List<String> topics;

    SearchIndexType(List<String> topics) {
        this.topics = topics;
    }

    public List<String> getTopics() {
        return topics;
    }
}
