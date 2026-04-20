package com.bondhub.socialfeedservice.service.seeder;

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
