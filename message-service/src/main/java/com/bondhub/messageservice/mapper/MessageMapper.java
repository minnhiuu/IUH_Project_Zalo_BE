package com.bondhub.messageservice.mapper;

import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.dto.response.ReplyMetadataResponse;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.common.dto.client.messageservice.ReplyMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MessageMapper {
    
    default OffsetDateTime map(LocalDateTime value) {
        if (value == null) return null;
        return value.atOffset(ZoneOffset.ofHours(7));
    }

    @Mapping(target = "senderAvatar", expression = "java(msg.getSenderAvatar() != null ? baseUrl + msg.getSenderAvatar() : null)")
    @Mapping(target = "replyTo", source = "msg.replyTo")
    @Mapping(target = "metadata", source = "msg.metadata")
    MessageResponse mapToMessageResponse(Message msg, String baseUrl);

    @Mapping(target = "senderAvatar", expression = "java(msg.getSenderAvatar() != null ? baseUrl + msg.getSenderAvatar() : null)")
    @Mapping(target = "timestamp", source = "msg.createdAt")
    @Mapping(target = "replyTo", source = "msg.replyTo")
    @Mapping(target = "unreadCount", source = "unreadCount")
    @Mapping(target = "metadata", source = "msg.metadata")
    ChatNotification mapToChatNotification(Message msg, String baseUrl, Integer unreadCount);

    ReplyMetadataResponse mapToReplyMetadataResponse(ReplyMetadata metadata);

    @Mapping(target = "messageId", source = "msg.id")
    @Mapping(target = "timestamp", source = "msg.createdAt")
    LastMessageInfo mapToLastMessageInfo(Message msg);
}
