package com.bondhub.userservice.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.userservice.model.enums.Gender;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDate;
import java.util.Set;

@Document("users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User extends BaseModel {
    @EqualsAndHashCode.Include
    @MongoId
    String id;

    String fullName;
    LocalDate dob;
    String bio;

    Gender gender;
    String accountId;
    Set<String> pinnedConversations;

    String avatar;
    String background;
    Double backgroundY;
}
