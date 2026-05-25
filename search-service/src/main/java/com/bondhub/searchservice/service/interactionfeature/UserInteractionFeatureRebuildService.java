package com.bondhub.searchservice.service.interactionfeature;

import com.bondhub.searchservice.dto.response.UserInteractionFeatureRebuildResponse;

public interface UserInteractionFeatureRebuildService {

    UserInteractionFeatureRebuildResponse rebuild(int sinceDays, int sourceLimit);
}
