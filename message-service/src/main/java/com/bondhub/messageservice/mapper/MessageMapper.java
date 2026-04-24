package com.bondhub.messageservice.mapper;

import com.bondhub.messageservice.dto.response.AttachmentInfoResponse;
import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.messageservice.dto.response.LinkPreviewResponse;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.dto.response.ReplyMetadataResponse;
import com.bondhub.messageservice.model.AttachmentInfo;
import com.bondhub.messageservice.model.LinkPreview;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.common.dto.client.messageservice.ReplyMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MessageMapper {

    default OffsetDateTime map(LocalDateTime value) {
        if (value == null) return null;
        return value.atOffset(ZoneOffset.ofHours(7));
    }

    @Mapping(target = "senderAvatar", expression = "java(msg.getSenderAvatar() != null ? baseUrl + msg.getSenderAvatar() : null)")
    @Mapping(target = "replyTo", source = "msg.replyTo")
    @Mapping(target = "metadata", source = "msg.metadata")
    @Mapping(target = "attachments", expression = "java(mapAttachments(msg.getAttachments(), baseUrl))")
    @Mapping(target = "linkPreview", source = "msg.linkPreview")
    @Mapping(target = "reactions", source = "msg.reactions")
    MessageResponse mapToMessageResponse(Message msg, String baseUrl);

    @Mapping(target = "senderAvatar", expression = "java(msg.getSenderAvatar() != null ? baseUrl + msg.getSenderAvatar() : null)")
    @Mapping(target = "timestamp", source = "msg.createdAt")
    @Mapping(target = "replyTo", source = "msg.replyTo")
    @Mapping(target = "unreadCount", source = "unreadCount")
    @Mapping(target = "metadata", source = "msg.metadata")
    @Mapping(target = "attachments", expression = "java(mapAttachments(msg.getAttachments(), baseUrl))")
    @Mapping(target = "linkPreview", source = "msg.linkPreview")
    @Mapping(target = "reactions", source = "msg.reactions")
    ChatNotification mapToChatNotification(Message msg, String baseUrl, Integer unreadCount);

    ReplyMetadataResponse mapToReplyMetadataResponse(ReplyMetadata metadata);

    AttachmentInfoResponse mapToAttachmentInfoResponse(AttachmentInfo info);

    LinkPreviewResponse mapToLinkPreviewResponse(LinkPreview linkPreview);

    LinkPreviewResponse.MemberSnapshot mapToMemberSnapshot(LinkPreview.MemberSnapshot snapshot);

    @Mapping(target = "messageId", source = "msg.id")
    @Mapping(target = "timestamp", source = "msg.createdAt")
    LastMessageInfo mapToLastMessageInfo(Message msg);

    default List<AttachmentInfoResponse> mapAttachments(List<AttachmentInfo> attachments, String baseUrl) {
        if (attachments == null) return null;
        return attachments.stream()
                .map(att -> AttachmentInfoResponse.builder()
                        .key(att.getKey())
                        .url(att.getKey() != null ? baseUrl + att.getKey(): null)
                        .fileName(att.getFileName())
                        .originalFileName(att.getOriginalFileName())
                        .contentType(att.getContentType())
                        .size(att.getSize())
                        .build())
                .collect(Collectors.toList());
    }
}
