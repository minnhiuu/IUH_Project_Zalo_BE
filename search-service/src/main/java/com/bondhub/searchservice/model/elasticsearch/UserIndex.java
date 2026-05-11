package com.bondhub.searchservice.model.elasticsearch;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

@Document(indexName = "users")
@Setting(settingPath = "elasticsearch/es-user-setting.json")
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserIndex {

    @Id
    String id;

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "name_index_analyzer",
                    searchAnalyzer = "name_search_analyzer"
            ),
            otherFields = {
                    @InnerField(
                            suffix = "fuzzy",
                            type = FieldType.Text,
                            analyzer = "name_fuzzy_analyzer",
                            searchAnalyzer = "name_fuzzy_analyzer"
                    ),
                    @InnerField(
                            suffix = "keyword",
                            type = FieldType.Keyword,
                            normalizer = "lowercase_normalizer"
                    ),
                    @InnerField(
                            suffix = "prefix",
                            type = FieldType.Text,
                            analyzer = "name_prefix_analyzer",
                            searchAnalyzer = "name_search_analyzer"
                    ),
                    @InnerField(
                            suffix = "shingle",
                            type = FieldType.Text,
                            analyzer = "name_shingle_analyzer",
                            searchAnalyzer = "name_search_analyzer"
                    )
            }
    )
    String fullName;

    @MultiField(
            mainField = @Field(type = FieldType.Keyword),
            otherFields = {
                    @InnerField(
                            suffix = "normalized",
                            type = FieldType.Keyword,
                            normalizer = "lowercase_normalizer"
                    )
            }
    )
    String phoneNumber;

    @Field(type = FieldType.Keyword)
    String accountId;

    @Field(type = FieldType.Keyword)
    String role;

    @Field(type = FieldType.Keyword, index = false)
    String avatar;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    LocalDateTime createdAt;
}
