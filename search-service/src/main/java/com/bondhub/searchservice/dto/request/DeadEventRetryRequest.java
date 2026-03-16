package com.bondhub.searchservice.dto.request;

import java.time.ZonedDateTime;

public record DeadEventRetryRequest(
    ZonedDateTime fromDate,
    ZonedDateTime toDate
) {}
