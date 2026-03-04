package com.bondhub.userservice.model.elasticsearch;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Document(indexName = "users")
@Setting(settingPath = "elasticsearch/es-setting.json")
@Builder
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserIndex {
    @Id
    String id;

    @Field(type = FieldType.Text,
            analyzer = "name_index_analyzer",
            searchAnalyzer = "name_search_analyzer")
    String fullName;

    @Field(type = FieldType.Keyword)
    String phoneNumber;

    @Field(type = FieldType.Keyword)
    String accountId;

    @Field(type = FieldType.Keyword)
    String role;

    @Field(type = FieldType.Keyword, index = false)
    String avatar;
}