package com.bondhub.aiservice.model;

import dev.langchain4j.data.message.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CragState {

    // Input
    private String userQuery;
    private String originalQuery;
    private String conversationId;
    private String userId;
    private String convId;
    private String currentTime;

    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    @Builder.Default
    private List<ChatMessage> history = new ArrayList<>();

    // Intermediate
    private String rewrittenQuery;
    private String route; // DIRECT, COMPLETE, MISSING:...
    private String internalContext;
    private String webContext;
    private String context;
    private String grade; // CORRECT, INCORRECT, AMBIGUOUS
    private String missingFieldInfo;
    private boolean resumedFromCheckpoint;
    private boolean lowConfidenceContext;
    private String qualityNote;

    @Builder.Default
    private int retryCount = 0;

    // Output
    private String finalAnswer;

    @Builder.Default
    private List<String> suggestedQuestions = new ArrayList<>();

    // Runtime / observability
    private String lastError;

    @Builder.Default
    private List<String> statusTrail = new ArrayList<>();
}
