package com.bondhub.userservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReindexStep {
    INITIALIZING(0, "search.re-index.step.initializing"),
    SYNCING_START(30, "search.re-index.step.syncing_start"),
    SYNCING_MID(60, "search.re-index.step.syncing_mid"),
    DATA_COMPLETE(90, "search.re-index.step.data_complete"),
    SWITCHING_ALIAS(95, "search.re-index.step.switching_alias"),
    CLEANING_UP(98, "search.re-index.step.cleaning_up"),
    FINALIZING(99, "search.re-index.step.finalizing"),
    COMPLETED(100, "search.re-index.step.completed");

    private final int percentage;
    private final String messageKey;
}
