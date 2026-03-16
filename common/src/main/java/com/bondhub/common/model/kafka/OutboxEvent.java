package com.bondhub.common.model.kafka;

import com.bondhub.common.model.BaseModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;
import java.time.Instant;

@Document("outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutboxEvent extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
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

    Instant processedAt;

    Integer retryCount;

    String errorMessage;

    public enum OutboxEventStatus {
        PENDING,
        PROCESSING,
        PUBLISHED,
        CONSUMED, // Consumer has successfully processed the event
        FAILED,
        DEAD
    }
}
