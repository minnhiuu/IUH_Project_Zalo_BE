package com.bondhub.aiservice.service.core.state;

import com.bondhub.aiservice.dto.AgentState;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentStateServiceImpl implements AgentStateService {

    private final ConcurrentHashMap<String, AgentState> storage = new ConcurrentHashMap<>();

    @Override
    public void save(String conversationId, AgentState state) {
        storage.put(conversationId, state);
    }

    @Override
    public AgentState get(String conversationId) {
        return storage.get(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        storage.remove(conversationId);
    }

    @Override
    public boolean hasState(String conversationId) {
        return storage.containsKey(conversationId);
    }
}
