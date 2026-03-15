package com.bondhub.messageservice.service;

import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.event.UserSyncEvent;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.repository.ChatRoomRepository;
import com.bondhub.messageservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.bondhub.common.dto.PageResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRoomService {
        private final ChatRoomRepository chatRoomRepository;
        private final ChatUserRepository chatUserRepository;
        private final SecurityUtil securityUtil;
        private final ApplicationEventPublisher eventPublisher;

        @Value("${aws.s3.bucket.name}")
        private String bucketName;

        @Value("${cloud.aws.region.static}")
        private String region;

        public String generateChatRoomId(String senderId, String recipientId) {
                return (senderId.compareTo(recipientId) < 0)
                                ? String.format("%s_%s", senderId, recipientId)
                                : String.format("%s_%s", recipientId, senderId);
        }

        public Optional<String> getChatRoomId(
                        String senderId,
                        String recipientId,
                        boolean createNewRoomIfNotExists) {
                String chatId = generateChatRoomId(senderId, recipientId);

                return chatRoomRepository
                                .findByChatId(chatId)
                                .map(Conversation::getChatId)
                                .or(() -> {
                                        if (createNewRoomIfNotExists) {
                                                Conversation conversation = Conversation
                                                                .builder()
                                                                .chatId(chatId)
                                                                .senderId(senderId)
                                                                .recipientId(recipientId)
                                                                .build();

                                                chatRoomRepository.save(conversation);

                                                return Optional.of(chatId);
                                        }

                                        return Optional.empty();
                                });
        }

        public Conversation createInitialChatRoom(String userA, String userB, LocalDateTime timestamp) {
                String chatId = generateChatRoomId(userA, userB);

                return chatRoomRepository.findByChatId(chatId).orElseGet(() -> {
                        Conversation newRoom = Conversation.builder()
                                        .chatId(chatId)
                                        .senderId(userA)
                                        .recipientId(userB)
                                        .lastMessage(null) // Empty message to trigger UI greeting
                                        .lastMessageTime(timestamp) // Allows sorting in inbox
                                        .build();
                        log.info("Created initial chat room proactively for: {}", chatId);
                        return chatRoomRepository.save(newRoom);
                });
        }

        public ConversationResponse getConversationForUser(String userId, String partnerId) {
                String chatId = generateChatRoomId(userId, partnerId);
                Conversation room = chatRoomRepository.findByChatId(chatId)
                        .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
                
                ChatUser partner = chatUserRepository.findById(partnerId)
                                .orElseGet(() -> ChatUser.builder().id(partnerId).fullName("Người dùng mới").build());

                String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

                return ConversationResponse.builder()
                                .chatId(room.getChatId())
                                .partnerId(partnerId)
                                .partnerName(partner.getFullName())
                                .partnerAvatar(partner.getAvatar() != null ? baseUrl + partner.getAvatar() : null)
                                .partnerStatus(partner.getStatus())
                                .lastSeenAt(partner.getLastUpdatedAt())
                                .lastMessage(room.getLastMessage())
                                .lastMessageTime(room.getLastMessageTime())
                                .unreadCount(room.getUnreadCounts() != null ? room.getUnreadCounts().getOrDefault(userId, 0) : 0)
                                .build();
        }

        public PageResponse<List<ConversationResponse>> getUserConversations(int page, int size) {
                String currentUserId = securityUtil.getCurrentUserId();
                
                Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastMessageTime"));
                Page<Conversation> roomsPage = chatRoomRepository.findAllRoomsByUserId(currentUserId, pageable);
                
                if (roomsPage.isEmpty()) {
                        return PageResponse.empty(pageable);
                }

                // 1. Lấy tất cả partnerId
                Set<String> allPartnerIds = roomsPage.getContent().stream()
                                .map(room -> room.getSenderId().equals(currentUserId) ? room.getRecipientId()
                                                : room.getSenderId())
                                .collect(Collectors.toSet());

                // 2. Query batch từ Mirror DB
                List<ChatUser> partners = chatUserRepository.findAllById(allPartnerIds);
                Map<String, ChatUser> partnerMap = partners.stream()
                                .collect(Collectors.toMap(ChatUser::getId, u -> u));

                // 3. Tìm những ID bị thiếu để bắn Event (Chỉ bắn 1 lần cho mỗi ID)
                allPartnerIds.stream()
                                .filter(id -> !partnerMap.containsKey(id))
                                .forEach(id -> eventPublisher.publishEvent(new UserSyncEvent(id)));

                // 4. Map sang Response
                String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
                return PageResponse.fromPage(roomsPage, room -> {
                        String partnerId = room.getSenderId().equals(currentUserId) ? room.getRecipientId()
                                        : room.getSenderId();
                        ChatUser partner = partnerMap.getOrDefault(partnerId,
                                        ChatUser.builder().id(partnerId).fullName("Người dùng mới").build());

                        return ConversationResponse.builder()
                                        .chatId(room.getChatId())
                                        .partnerId(partnerId)
                                        .partnerName(partner.getFullName())
                                        .partnerAvatar(partner.getAvatar() != null ? baseUrl + partner.getAvatar() : null)
                                        .partnerStatus(partner.getStatus())
                                        .lastSeenAt(partner.getLastUpdatedAt())
                                        .lastMessage(room.getLastMessage())
                                        .lastMessageTime(room.getLastMessageTime())
                                        .unreadCount(room.getUnreadCounts() != null ? room.getUnreadCounts().getOrDefault(currentUserId, 0) : 0)
                                        .build();
                });
        }

        public void markAsRead(String chatId) {
                String currentUserId = securityUtil.getCurrentUserId();
                Conversation room = chatRoomRepository.findByChatId(chatId)
                        .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

                if (!room.getSenderId().equals(currentUserId) && !room.getRecipientId().equals(currentUserId)) {
                        throw new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND);
                }

                if (room.getUnreadCounts() != null && room.getUnreadCounts().getOrDefault(currentUserId, 0) > 0) {
                        room.getUnreadCounts().put(currentUserId, 0);
                        chatRoomRepository.save(room);
                }
        }
}
