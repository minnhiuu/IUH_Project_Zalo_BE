package com.bondhub.aiservice.service.retrival.memory;

public interface MessageIngestionService {

    void ingest(String messageId, String content, String userId, String chatId);
}
