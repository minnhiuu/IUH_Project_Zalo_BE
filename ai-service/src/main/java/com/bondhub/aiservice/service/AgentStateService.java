package com.bondhub.aiservice.service;

import com.bondhub.aiservice.dto.AgentState;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentStateService {

    private final ConcurrentHashMap<String, AgentState> storage = new ConcurrentHashMap<>();

    public void save(String conversationId, AgentState state) {
        storage.put(conversationId, state);
    }

    public AgentState get(String conversationId) {
        return storage.get(conversationId);
    }

    public void clear(String conversationId) {
        storage.remove(conversationId);
    }

    public boolean hasState(String conversationId) {
        return storage.containsKey(conversationId);
    }
}
