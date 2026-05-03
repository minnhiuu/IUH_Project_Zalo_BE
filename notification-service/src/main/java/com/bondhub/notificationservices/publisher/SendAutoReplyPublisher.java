package com.bondhub.notificationservices.publisher;

import com.bondhub.common.event.chat.SendAutoReplyEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SendAutoReplyPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;

    static String TOPIC = "chat.auto-reply";

    public void publish(SendAutoReplyEvent event) {
        kafkaTemplate.send(TOPIC, event.getConversationId(), event);

        log.info("[AutoReplyPublisher] Published event: conversation={}, receiver={}",
                event.getConversationId(),
                event.getReceiverId());
    }
}
