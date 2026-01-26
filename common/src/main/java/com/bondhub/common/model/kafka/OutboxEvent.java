package com.bondhub.common.model.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Document("outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutboxEvent {
    
    @MongoId
    String id;
    
    @Indexed
    String aggregateId;
    
    @Indexed
    String aggregateType;
    
    @Indexed
    EventType eventType;
    
    String payload;
    
    @Indexed
    @Builder.Default
    OutboxEventStatus status = OutboxEventStatus.PENDING;
    
    @Indexed
    @Builder.Default
    Instant createdAt = Instant.now();
    
    Instant processedAt;
    
    Integer retryCount;
    
    String errorMessage;
    
    public enum OutboxEventStatus {
        PENDING,
        PROCESSING,
        PUBLISHED,
        CONSUMED,  // Consumer has successfully processed the event
        FAILED,
        DEAD
    }
}
