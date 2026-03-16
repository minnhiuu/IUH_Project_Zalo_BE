package com.bondhub.userservice.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.userservice.model.enums.Gender;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Document("users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User extends BaseModel {
    @EqualsAndHashCode.Include
    @MongoId(FieldType.OBJECT_ID)
    String id;

    String fullName;
    LocalDate dob;
    String bio;

    Gender gender;
    @Field(targetType = FieldType.OBJECT_ID)
    String accountId;
    Set<String> pinnedConversations;

    String avatar;
    String background;
    Double backgroundY;

    /** Updated on every successful login (set by auth-service via internal API) */
    LocalDateTime lastLoginAt;
}
