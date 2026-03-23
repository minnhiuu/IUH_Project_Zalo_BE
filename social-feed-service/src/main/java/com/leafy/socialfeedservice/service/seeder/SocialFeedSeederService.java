package com.leafy.socialfeedservice.service.seeder;

import com.leafy.socialfeedservice.dto.response.account.UserSeedInfo;

import java.util.List;
import java.util.Map;

public interface SocialFeedSeederService {

    /**
     * One-shot pipeline:
     * <ol>
     *   <li>Fetch real users from auth-service</li>
     *   <li>Publish {@code UserCreatedEvent} with random interests (→ Qdrant vector indexing)</li>
     *   <li>Seed mock Posts, Comments, and Reactions</li>
     * </ol>
     *
     * @return combined summary map
     */
    Map<String, Object> seedEverything();
}
