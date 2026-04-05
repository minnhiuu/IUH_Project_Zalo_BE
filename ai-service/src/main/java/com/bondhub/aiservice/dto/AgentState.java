package com.bondhub.aiservice.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentState {
    private String conversationId;
    private String lastQuery;       // Câu hỏi làm rõ đã hỏi user
    private String originalQuery;   // Câu hỏi GỐC của user trước khi bị MISSING
    private String currentState;    // START, WAIT_FOR_CONTEXT, COMPLETED
    private List<String> context;
}
