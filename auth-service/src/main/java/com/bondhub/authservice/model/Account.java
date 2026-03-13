package com.bondhub.authservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.bondhub.common.model.BaseModel;
import com.bondhub.common.enums.Role;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document("accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Account extends BaseModel {
    @EqualsAndHashCode.Include
    @MongoId(FieldType.OBJECT_ID)
    String id;

    String email;
    String phoneNumber;

    String password;

    Role role;

    @Builder.Default
    Boolean isVerified = false;

    @Builder.Default
    Boolean enabled = true;
}
