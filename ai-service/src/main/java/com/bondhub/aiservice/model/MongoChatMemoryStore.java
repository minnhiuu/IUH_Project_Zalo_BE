package com.bondhub.aiservice.model;

import dev.langchain4j.data.message.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class MongoChatMemoryStore implements dev.langchain4j.store.memory.chat.ChatMemoryStore {

    private final MongoTemplate mongoTemplate;
    private static final String COLLECTION_NAME = "ai_chat_memory";

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            RawChatMemory raw = mongoTemplate.findOne(
                    Query.query(Criteria.where("_id").is(memoryId.toString())),
                    RawChatMemory.class, COLLECTION_NAME);
            if (raw == null || raw.getMessages() == null)
                return new ArrayList<>();
            return raw.getMessages().stream()
                    .map(MongoChatMemoryStore::fromDoc)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[ChatMemory] Failed to load memory for conv: {}. Clearing. Error: {}", memoryId, e.getMessage());
            deleteMessages(memoryId);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        List<Map<String, Object>> docs = messages.stream()
                .map(MongoChatMemoryStore::toDoc)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        RawChatMemory raw = new RawChatMemory(memoryId.toString(), docs);
        mongoTemplate.save(raw, COLLECTION_NAME);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(memoryId.toString())),
                COLLECTION_NAME);
    }

    private static Map<String, Object> toDoc(ChatMessage msg) {
        Map<String, Object> doc = new LinkedHashMap<>();
        if (msg instanceof UserMessage um) {
            doc.put("type", "USER");
            doc.put("text", um.singleText());
        } else if (msg instanceof AiMessage ai) {
            doc.put("type", "AI");
            doc.put("text", ai.text() != null ? ai.text() : "");
            if (ai.hasToolExecutionRequests()) {
                List<Map<String, String>> reqs = ai.toolExecutionRequests().stream()
                        .map(r -> Map.of("id", r.id(), "name", r.name(), "args", r.arguments()))
                        .collect(Collectors.toList());
                doc.put("toolRequests", reqs);
            }
        } else if (msg instanceof ToolExecutionResultMessage tr) {
            doc.put("type", "TOOL_RESULT");
            doc.put("toolCallId", tr.id());
            doc.put("toolName", tr.toolName());
            doc.put("text", tr.text());
        } else if (msg instanceof SystemMessage sm) {
            doc.put("type", "SYSTEM");
            doc.put("text", sm.text());
        } else {
            log.debug("[ChatMemory] Unknown message type, skipping: {}", msg.getClass().getSimpleName());
            return null;
        }
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static ChatMessage fromDoc(Map<String, Object> doc) {
        String type = (String) doc.get("type");
        String text = (String) doc.getOrDefault("text", "");
        if (type == null)
            return null;
        return switch (type) {
            case "USER" -> UserMessage.from(text);
            case "AI" -> {
                List<Map<String, String>> reqs = (List<Map<String, String>>) doc.get("toolRequests");
                if (reqs != null && !reqs.isEmpty()) {
                    List<ToolExecutionRequest> toolReqs = reqs.stream()
                            .map(r -> ToolExecutionRequest.builder()
                                    .id(r.get("id"))
                                    .name(r.get("name"))
                                    .arguments(r.get("args"))
                                    .build())
                            .collect(Collectors.toList());
                    yield AiMessage.from(toolReqs);
                }
                yield AiMessage.from(text);
            }
            case "TOOL_RESULT" -> ToolExecutionResultMessage.from(
                    (String) doc.get("toolCallId"),
                    (String) doc.get("toolName"),
                    text);
            case "SYSTEM" -> SystemMessage.from(text);
            default -> {
                log.debug("[ChatMemory] Unknown type in DB: {}", type);
                yield null;
            }
        };
    }

    private static final Set<String> TECHNICAL_TOKENS = Set.of(
            "COMPLETE", "DIRECT", "MISSING", "CORRECT", "INCORRECT",
            "AMBIGUOUS", "NEW_INTENT", "CONTINUE", "WAIT_FOR_CONTEXT");

    public String getCleanHistory(Object memoryId, int limit) {
        List<ChatMessage> allMessages = getMessages(memoryId);
        if (allMessages.isEmpty())
            return "";

        List<String> cleaned = allMessages.stream()
                .filter(msg -> !(msg instanceof SystemMessage))
                .filter(msg -> !(msg instanceof ToolExecutionResultMessage))
                .filter(msg -> {
                    String text = extractText(msg).toUpperCase();
                    return !text.isEmpty() && TECHNICAL_TOKENS.stream().noneMatch(text::equals);
                })
                .map(msg -> {
                    boolean isUser = msg instanceof UserMessage;
                    return (isUser ? "User" : "AI") + ": " + extractText(msg);
                })
                .collect(Collectors.toList());

        int from = Math.max(0, cleaned.size() - limit);
        return String.join("\n", cleaned.subList(from, cleaned.size()));
    }

    private static String extractText(ChatMessage msg) {
        try {
            if (msg instanceof UserMessage um)
                return um.singleText() != null ? um.singleText().trim() : "";
            if (msg instanceof AiMessage ai)
                return ai.text() != null ? ai.text().trim() : "";
        } catch (Exception ignored) {
        }
        return "";
    }
}
