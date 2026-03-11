package com.bondhub.searchservice.model.mongodb;

import com.bondhub.common.model.kafka.EventType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.LocalDateTime;

@Document(collection = "failed_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedEvent {

    @Id
    String id;

    @Indexed
    @Field(targetType = FieldType.OBJECT_ID)
    String eventId;

    EventType eventType;

    String topic;

    String payload;

    String errorMessage;

    String stackTrace;

    Integer partition;

    Long offset;

    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    int retryCount = 0;

    @Builder.Default
    boolean resolved = false;
}
