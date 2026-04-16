package com.bondhub.messageservice.model;

import com.bondhub.common.model.BaseModel;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "call_sessions")
@CompoundIndex(name = "caller_status_idx", def = "{'callerId': 1, 'status': 1}")
@CompoundIndex(name = "receiver_status_idx", def = "{'receiverId': 1, 'status': 1}")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class CallSession extends BaseModel {

    @Id
    String id;

    String callerId;
    String callerName;
    String callerAvatar;

    String receiverId;
    String receiverName;
    String receiverAvatar;

    String roomId;

    @Builder.Default
    CallStatus status = CallStatus.RINGING;

    LocalDateTime startTime;
    LocalDateTime endTime;

    public enum CallStatus {
        RINGING,
        IN_PROGRESS,
        ENDED,
        MISSED,
        REJECTED,
        CANCELLED
    }
}
