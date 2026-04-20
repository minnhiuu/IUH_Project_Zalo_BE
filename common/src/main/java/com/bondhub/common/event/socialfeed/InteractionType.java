package com.bondhub.common.event.socialfeed;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InteractionType {
    VIEW(1.0f),
    LIKE(3.0f),
    COMMENT(5.0f),
    SHARE(7.0f),
    DISLIKE(-5.0f);

    private final float weight;
}