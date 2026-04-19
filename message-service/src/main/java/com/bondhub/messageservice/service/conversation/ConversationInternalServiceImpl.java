package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.client.messageservice.ConversationMemberLookupResponse;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationInternalServiceImpl implements ConversationInternalService {

    private final ConversationRepository conversationRepository;
    private final ConversationHelper helper;

    @Override
    public ConversationMemberLookupResponse getConversationMember(String conversationId, String userId) {
        ConversationMember member = conversationRepository.findById(conversationId)
                .flatMap(conversation -> conversation.getMembers().stream()
                        .filter(helper::isActiveMember)
                        .filter(item -> item.getUserId().equals(userId))
                        .findFirst())
                .orElse(null);

        if (member == null) {
            return ConversationMemberLookupResponse.builder()
                    .member(false)
                    .joinedAt(null)
                    .build();
        }

        return ConversationMemberLookupResponse.builder()
                .member(true)
                .joinedAt(member.getJoinedAt() != null
                        ? member.getJoinedAt().atZone(ZoneId.systemDefault()).toInstant()
                        : null)
                .build();
    }
}
