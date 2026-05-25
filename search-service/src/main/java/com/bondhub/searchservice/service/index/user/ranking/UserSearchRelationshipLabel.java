package com.bondhub.searchservice.service.index.user.ranking;

import java.util.List;

public record UserSearchRelationshipLabel(
        String messageKey,
        List<Object> args
) {
    public static UserSearchRelationshipLabel of(String messageKey, Object... args) {
        return new UserSearchRelationshipLabel(messageKey, List.of(args));
    }
}
