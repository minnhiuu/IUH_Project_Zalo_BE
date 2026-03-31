package com.bondhub.aiservice.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentState {
    private String conversationId;
    private String lastQuery;
    private String currentState; // START, WAIT_FOR_CONTEXT, COMPLETED
    private List<String> context;
}
