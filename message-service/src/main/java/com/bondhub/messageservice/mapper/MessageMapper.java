package com.bondhub.messageservice.mapper;

import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.dto.response.ReplyMetadataResponse;
import com.bondhub.messageservice.model.Message;
import com.bondhub.common.dto.client.messageservice.ReplyMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MessageMapper {

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
}
