package com.bondhub.socialfeedservice.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.socialfeedservice.model.enums.AdminAction;
import com.bondhub.socialfeedservice.model.enums.ReportReason;
import com.bondhub.socialfeedservice.model.enums.ReportStatus;
import com.bondhub.socialfeedservice.model.enums.TargetType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Document("reports")
public class Report extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    @Indexed
    String reporterId;

    @Indexed
    String targetId;

    TargetType targetType;

    ReportReason reason;

    String details;

    @Builder.Default
    @Indexed
    ReportStatus status = ReportStatus.PENDING;

    String adminId;

    String adminNote;

    AdminAction adminAction;
}
