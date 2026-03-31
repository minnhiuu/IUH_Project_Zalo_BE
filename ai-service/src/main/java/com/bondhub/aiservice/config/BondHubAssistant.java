package com.bondhub.aiservice.config;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import org.springframework.stereotype.Component;

@Component
public interface BondHubAssistant {
    TokenStream chat(@MemoryId String userId, @UserMessage String userMessage);
}
