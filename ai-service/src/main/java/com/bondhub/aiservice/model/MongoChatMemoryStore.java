package com.bondhub.aiservice.model;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class MongoChatMemoryStore implements ChatMemoryStore {
    private final MongoTemplate mongoTemplate;
    private static final String COLLECTION_NAME = "ai_chat_memory";

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        log.debug("Retrieving chat memory for: {}", memoryId);
        try {
            ChatMemory entity = mongoTemplate.findOne(
                    Query.query(Criteria.where("chatId").is(memoryId.toString())),
                    ChatMemory.class,
                    COLLECTION_NAME
            );

            if (entity == null || entity.getMessages() == null) return new ArrayList<>();

            // Filter out nulls from old/malformed documents that lack 'type' field
            return entity.getMessages().stream()
                    .filter(m -> m != null)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            log.warn("[ChatMemory] Failed to deserialize memory for conv: {}. Clearing corrupted data. Error: {}",
                    memoryId, e.getMessage());
            // Auto-remove corrupted memory to prevent repeated crashes
            deleteMessages(memoryId);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        log.debug("Updating chat memory for: {}", memoryId);
        ChatMemory entity = ChatMemory.builder()
                .chatId(memoryId.toString())
                .messages(messages)
                .build();
        mongoTemplate.save(entity, COLLECTION_NAME);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        log.debug("Deleting chat memory for: {}", memoryId);
        mongoTemplate.remove(
                Query.query(Criteria.where("chatId").is(memoryId.toString())),
                COLLECTION_NAME
        );
    }

    // ── Clean History for QueryRewriter ──────────────────────────────────────

    /**
     * Token kỹ thuật bị nhiễm vào ChatMemory do AnalyzerService / GraderService
     * từng có chatMemoryProvider. Cần lọc ra trước khi dùng làm context.
     */
    private static final Set<String> TECHNICAL_TOKENS = Set.of(
            "COMPLETE", "DIRECT", "MISSING", "CORRECT", "INCORRECT",
            "AMBIGUOUS", "NEW_INTENT", "CONTINUE", "WAIT_FOR_CONTEXT"
    );

    /**
     * Lấy lịch sử chat "sạch" từ MongoDB, loại bỏ:
     * <ul>
     *   <li>SystemMessage (prompt kỹ thuật hệ thống)</li>
     *   <li>Các token điều hướng bị nhiễm (COMPLETE, INCORRECT…)</li>
     *   <li>Tin nhắn rỗng / null</li>
     * </ul>
     *
     * @param memoryId convId (format: conversationId:userId)
     * @param limit    Số message cuối cần lấy (khuyến nghị 6 = 3 turns user+AI)
     * @return Chuỗi "User: ...\nAI: ..." sẵn sàng truyền vào QueryRewriter
     */
    public String getCleanHistory(Object memoryId, int limit) {
        List<ChatMessage> allMessages = getMessages(memoryId);
        if (allMessages.isEmpty()) return "";

        List<String> cleaned = allMessages.stream()
                .filter(msg -> !(msg instanceof dev.langchain4j.data.message.SystemMessage))
                .filter(msg -> {
                    String text = extractText(msg);
                    return !text.isEmpty() && !TECHNICAL_TOKENS.contains(text.toUpperCase());
                })
                .map(msg -> {
                    boolean isUser = msg instanceof dev.langchain4j.data.message.UserMessage;
                    return (isUser ? "User" : "AI") + ": " + extractText(msg);
                })
                .collect(java.util.stream.Collectors.toList());

        // Lấy `limit` entries cuối cùng
        int from = Math.max(0, cleaned.size() - limit);
        return String.join("\n", cleaned.subList(from, cleaned.size()));
    }

    /** Trích xuất nội dung text từ ChatMessage không dùng deprecated .text() */
    private static String extractText(ChatMessage msg) {
        try {
            if (msg instanceof dev.langchain4j.data.message.UserMessage um) {
                return um.singleText() != null ? um.singleText().trim() : "";
            }
            if (msg instanceof dev.langchain4j.data.message.AiMessage ai) {
                return ai.text() != null ? ai.text().trim() : "";
            }
        } catch (Exception ignored) {}
        return "";
    }
}
