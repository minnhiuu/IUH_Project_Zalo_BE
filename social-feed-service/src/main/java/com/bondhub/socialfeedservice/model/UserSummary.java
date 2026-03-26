package com.bondhub.socialfeedservice.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * Local snapshot of a user's public display info, kept in-sync via Kafka events.
 * Used to enrich PostResponse without an outbound call to user-service at read time.
 */
@Document("user_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSummary {

    @MongoId(FieldType.STRING)
    String id; // equals userId

    String fullName;
    String avatar;
}
