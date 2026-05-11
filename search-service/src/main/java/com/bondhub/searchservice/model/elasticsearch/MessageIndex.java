package com.bondhub.searchservice.model.elasticsearch;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import java.util.List;

@Document(indexName = "#{@elasticsearchProperties.messageAlias}")
@Setting(settingPath = "elasticsearch/es-message-setting.json")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageIndex {

    @Id
    String id;

    @Field(type = FieldType.Keyword)
    String conversationId;

    @Field(type = FieldType.Keyword)
    List<String> participantIds;

    @Field(type = FieldType.Keyword, index = false)
    List<String> participantNames;

    @Field(type = FieldType.Keyword, index = false)
    List<String> participantAvatars;

    @Field(type = FieldType.Keyword, index = false)
    String conversationName;

    @Field(type = FieldType.Keyword, index = false)
    String conversationAvatar;

    @Field(type = FieldType.Boolean)
    boolean group;

    @Field(type = FieldType.Keyword)
    String senderId;

    @Field(type = FieldType.Keyword, index = false)
    String senderName;

    @Field(type = FieldType.Keyword, index = false)
    String senderAvatar;

    @Field(type = FieldType.Text, index = false)
    String content;

    @Field(type = FieldType.Keyword, index = false)
    String linkGroupName;

    @Field(type = FieldType.Keyword, index = false)
    String linkUrl;

    @Field(type = FieldType.Keyword, index = false)
    String originalFileName;

    @Field(type = FieldType.Keyword)
    String fileExtension;

    @Field(type = FieldType.Long, index = false)
    Long size;

    @Field(
            type = FieldType.Text,
            analyzer = "searchable_text_index_analyzer",
            searchAnalyzer = "searchable_text_search_analyzer"
    )
    String searchableText;

    @Field(
            type = FieldType.Text,
            analyzer = "searchable_text_index_analyzer",
            searchAnalyzer = "searchable_text_search_analyzer"
    )
    String conversationSearchText;

    @Field(type = FieldType.Keyword)
    String type;

    @Field(type = FieldType.Keyword)
    String status;

    @Field(type = FieldType.Boolean)
    boolean hasAttachment;

    @Field(type = FieldType.Boolean)
    boolean hasLink;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    Instant createdAt;

    @Field(type = FieldType.Keyword)
    List<String> deletedBy;

    @Field(type = FieldType.Keyword)
    List<String> visibleTo;
}
