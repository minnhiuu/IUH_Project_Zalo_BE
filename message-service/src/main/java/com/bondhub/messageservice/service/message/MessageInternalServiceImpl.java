package com.bondhub.messageservice.service.message;

import com.bondhub.messageservice.dto.response.MessageSyncResponse;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageInternalServiceImpl implements MessageInternalService {

    private final MessageRepository messageRepository;

    @Override
    public List<MessageSyncResponse> getMessagesBatch(String lastId, int batchSize) {
        Pageable pageable = PageRequest.of(0, batchSize);
        List<Message> messages;

        if (lastId == null || lastId.isEmpty()) {
            messages = messageRepository.findAllByOrderByIdAsc(pageable);
        } else {
            messages = messageRepository.findByIdGreaterThanOrderByIdAsc(lastId, pageable);
        }

        return messages.stream().map(this::mapToSyncResponse).collect(Collectors.toList());
    }

    @Override
    public long getMessageCount() {
        return messageRepository.count();
    }

    private MessageSyncResponse mapToSyncResponse(Message message) {
        boolean hasAttachment = message.getAttachments() != null && !message.getAttachments().isEmpty();
        boolean hasLink = message.getLinkPreview() != null;
        
        String originalFileName = null;
        Long size = null;
        String linkGroupName = null;

        if (hasAttachment) {
            var first = message.getAttachments().getFirst();
            originalFileName = first.getOriginalFileName() != null ? first.getOriginalFileName() : first.getFileName();
            size = first.getSize();
        }

        String linkUrl = hasLink ? message.getLinkPreview().getUrl() : null;

        return MessageSyncResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .senderAvatar(message.getSenderAvatar())
                .content(message.getContent())
                .type(message.getType())
                .status(message.getStatus())
                .hasAttachment(hasAttachment)
                .hasLink(hasLink)
                .linkGroupName(linkGroupName)
                .linkUrl(linkUrl)
                .originalFileName(originalFileName)
                .size(size)
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt().toInstant(ZoneOffset.UTC) : null)
                .deletedBy(message.getDeletedBy())
                .visibleTo(message.getVisibleTo())
                .build();
    }
}
