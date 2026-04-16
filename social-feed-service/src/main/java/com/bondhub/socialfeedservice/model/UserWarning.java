package com.bondhub.socialfeedservice.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.socialfeedservice.model.enums.ReportReason;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document("user_warnings")
public class UserWarning extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    @Indexed
    String userId;

    String reportId;

    ReportReason reason;

    String adminId;

    String adminNote;
}
