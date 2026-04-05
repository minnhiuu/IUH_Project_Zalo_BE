package com.bondhub.aiservice.service.core.state;

import com.bondhub.aiservice.dto.AgentState;

public interface AgentStateService {

    void save(String conversationId, AgentState state);

    AgentState get(String conversationId);

    void clear(String conversationId);

    boolean hasState(String conversationId);
}
