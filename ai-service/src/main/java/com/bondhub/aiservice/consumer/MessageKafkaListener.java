package com.bondhub.aiservice.consumer;

import com.bondhub.aiservice.service.MessageIngestionService;
import com.bondhub.common.event.ai.AiMessageSaveEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class MessageKafkaListener {

    private final MessageIngestionService ingestionService;

    /**
     * Lắng nghe cả tin nhắn của User ("message-created") và tin nhắn của AI ("ai.message.save").
     * Đảm bảo mọi tin nhắn mới đều được Vectorize đồng bộ vào Qdrant.
     */
    @KafkaListener(topics = {"message-created", "ai.message.save"}, groupId = "ai-service-group")
    public void onMessageEvent(AiMessageSaveEvent event, org.springframework.kafka.support.Acknowledgment ack) {
        log.info("[Kafka] Received event to ingest in room: {}", event.getChatId());
        
        try {
            ingestionService.ingest(
                java.util.UUID.randomUUID().toString(),
                event.getContent(),
                event.getUserId(),
                event.getChatId()
            );
            ack.acknowledge(); // Chỉ commit khi Ingestion thành công
        } catch (Exception e) {
            log.error("[Kafka] Ingestion failed for room: {}. Error: {}", event.getChatId(), e.getMessage());
            // Tuỳ chọn: Không ack để retry hoặc đưa vào Dead Letter Queue
        }
    }
}
