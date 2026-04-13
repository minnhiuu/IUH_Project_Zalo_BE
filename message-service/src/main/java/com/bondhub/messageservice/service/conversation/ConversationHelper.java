package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.enums.Status;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.messageservice.dto.response.*;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shared helpers used by both ConversationServiceImpl and GroupConversationServiceImpl.
 */
@Component
@RequiredArgsConstructor
@Getter
public class ConversationHelper {

    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final SecurityUtil securityUtil;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${kafka.topics.socket-events}")
    private String socketEventsTopic;

    public void assertMember(Conversation room, String userId) {
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId) && isActiveMember(m));
        if (!isMember) {
            throw new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND);
        }
    }

    public ConversationMember getMemberOrThrow(Conversation room, String userId) {
        return room.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && isActiveMember(m))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND));
    }

    public boolean isActiveMember(ConversationMember member) {
        return !Boolean.FALSE.equals(member.getActive());
    }

    public MemberRole resolveRole(ConversationMember member) {
        return member.getRole() != null ? member.getRole() : MemberRole.MEMBER;
    }

    public void assertOwnerOrAdmin(ConversationMember actor) {
        MemberRole actorRole = resolveRole(actor);
        if (actorRole == MemberRole.MEMBER) {
            throw new AppException(ErrorCode.CHAT_NOT_GROUP_MANAGER);
        }
    }

    public void assertCanRemoveMember(ConversationMember actor, ConversationMember target) {
        MemberRole actorRole = resolveRole(actor);
        MemberRole targetRole = resolveRole(target);

        if (targetRole == MemberRole.OWNER) {
            throw new AppException(ErrorCode.CHAT_CANNOT_REMOVE_OWNER);
        }
        if (actorRole == MemberRole.ADMIN && targetRole != MemberRole.MEMBER) {
            throw new AppException(ErrorCode.CHAT_ADMIN_CAN_ONLY_REMOVE_MEMBER);
        }
    }

    public ConversationResponse buildConversationResponseForCurrentUser(Conversation room, String currentUserId) {
        Set<String> allUserIds = room.getMembers().stream()
                .filter(this::isActiveMember)
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        if (room.getLastMessage() != null && room.getLastMessage().getSenderId() != null) {
            allUserIds.add(room.getLastMessage().getSenderId());
        }

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        ChatUser partner = null;
        if (!room.isGroup()) {
            String partnerId = room.getMembers().stream()
                    .filter(this::isActiveMember)
                    .map(ConversationMember::getUserId)
                    .filter(uid -> !uid.equals(currentUserId))
                    .findFirst()
                    .orElse(currentUserId);
            partner = resolvePartner(partnerId, currentUserId, userCache);
        }

        boolean viewerCanSee = canViewerSeeStatus(currentUserId, userCache);
        return buildConversationResponse(room, partner, currentUserId, userCache, baseUrl, viewerCanSee, null);
    }

    public ConversationResponse buildConversationResponse(
            Conversation room, ChatUser partner, String currentUserId,
            Map<String, ChatUser> userCache, String baseUrl, boolean viewerCanSee, String friendshipStatus) {

        LastMessageInfo last = room.getLastMessage();
        List<ConversationMemberResponse> members = buildMembersWithCache(
                room, currentUserId, userCache, baseUrl, viewerCanSee);

        String partnerDisplayName = safeDisplayName(partner != null ? partner.getFullName() : null);
        String displayName = room.getName();
        if (room.isGroup() && (displayName == null || displayName.isBlank())) {
            displayName = getDynamicGroupName(room, currentUserId, userCache);
        } else if (!room.isGroup()) {
            displayName = partnerDisplayName;
            if (displayName == null || displayName.isBlank()) {
                displayName = "Người dùng";
            }
        }
        String displayAvatar = room.isGroup()
                ? (room.getAvatar() != null ? baseUrl + room.getAvatar() : null)
                : (partner != null && partner.getAvatar() != null ? baseUrl + partner.getAvatar() : null);

        boolean isFriend = !room.isGroup() && "ACCEPTED".equals(friendshipStatus);

        Status displayStatus = room.isGroup()
                ? (room.getMembers().stream()
                .filter(m -> {
                    if (!isActiveMember(m)) return false;
                    if (m.getUserId().equals(currentUserId)) return false;
                    ChatUser memberInfo = userCache.get(m.getUserId());
                    return memberInfo == null || !currentUserId.equals(memberInfo.getAccountId());
                })
                .map(m -> userCache.get(m.getUserId()))
                .filter(Objects::nonNull)
                .anyMatch(u -> u.getStatus() == Status.ONLINE) ? Status.ONLINE : Status.OFFLINE)
                : (isFriend ? partner.getStatus() : null);

        return ConversationResponse.builder()
                .id(room.getId())
                .recipientId(room.isGroup() ? null : (partner != null ? partner.getId() : null))
                .name(displayName)
                .avatar(displayAvatar)
                .status(displayStatus)
                .lastSeenAt(isFriend && partner != null ? toOffset(partner.getLastUpdatedAt()) : null)
                .friendshipStatus(friendshipStatus)
                .isGroup(room.isGroup())
                .isDisbanded(room.isDisbanded())
                .unreadCount(room.getUnreadCounts() != null
                        ? room.getUnreadCounts().getOrDefault(currentUserId, 0) : 0)
                .lastMessage(last != null ? LastMessageResponse.builder()
                        .id(last.getMessageId())
                        .senderId(last.getSenderId())
                        .senderName(last.getSenderId() != null
                                ? userCache.getOrDefault(last.getSenderId(),
                                ChatUser.builder().fullName("").build()).getFullName() : null)
                        .content(last.getContent())
                        .timestamp(toOffset(last.getTimestamp()))
                        .type(last.getType())
                        .status(last.getStatus())
                        .isFromMe(last.getSenderId() != null && last.getSenderId().equals(currentUserId))
                        .metadata(last.getMetadata())
                        .build() : null)
                .members(members)
                .build();
    }

    public void broadcastConversationUpdate(String conversationId) {
        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        broadcastConversationUpdate(room);
    }

    public void broadcastConversationUpdate(Conversation room) {
        Set<String> userIds = room.getMembers().stream()
                .filter(this::isActiveMember)
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        for (ConversationMember member : room.getMembers()) {
            if (!isActiveMember(member)) continue;
            String viewerId = member.getUserId();
            boolean viewerCanSee = canViewerSeeStatus(viewerId, userCache);

            ChatUser partner = null;
            if (!room.isGroup()) {
                String partnerId = room.getMembers().stream()
                        .filter(this::isActiveMember)
                        .map(ConversationMember::getUserId)
                        .filter(uid -> !uid.equals(viewerId))
                        .findFirst()
                        .orElse(viewerId);
                partner = resolvePartner(partnerId, viewerId, userCache);
            }

            ConversationResponse payload = buildConversationResponse(
                    room, partner, viewerId, userCache, baseUrl, viewerCanSee, null
            );

            kafkaTemplate.send(socketEventsTopic,
                    new SocketEvent(SocketEventType.CONVERSATION, viewerId, "/queue/conversations", payload));
        }
    }

    public ChatUser resolvePartner(String partnerId, String currentUserId, Map<String, ChatUser> userCache) {
        if (partnerId.equals(currentUserId)) {
            return ChatUser.builder()
                    .id(partnerId)
                    .fullName("My Documents")
                    .avatar("cloud.png")
                    .showSeenStatus(false)
                    .build();
        }
        return userCache.getOrDefault(partnerId,
                ChatUser.builder().id(partnerId).fullName("Người dùng mới").build());
    }

    public boolean canViewerSeeStatus(String currentUserId, Map<String, ChatUser> userCache) {
        ChatUser currentUser = userCache.get(currentUserId);
        return currentUser != null && currentUser.isShowSeenStatus();
    }

    public String getDynamicGroupName(Conversation room, String currentUserId, Map<String, ChatUser> userCache) {
        List<String> memberNames = room.getMembers().stream()
                .filter(this::isActiveMember)
                .filter(m -> {
                    if (room.getMembers().size() <= 2) return true;
                    return !m.getUserId().equals(currentUserId);
                })
                .map(m -> {
                    ChatUser u = userCache.get(m.getUserId());
                    return u != null ? u.getFullName() : "Người dùng";
                })
                .filter(name -> name != null && !name.isBlank())
                .limit(4) 
                .toList();

        if (memberNames.isEmpty()) return "Nhóm";

        String joined = String.join(", ", memberNames);
        int totalMembers = (int) room.getMembers().stream().filter(this::isActiveMember).count();
        int remaining = totalMembers - memberNames.size();

        if (remaining > 0) {
            return joined + " và " + remaining + " người khác";
        }
        return joined;
    }

    public String safeDisplayName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Người dùng";
        return fullName;
    }

    private List<ConversationMemberResponse> buildMembersWithCache(
            Conversation room, String currentUserId, Map<String, ChatUser> userCache,
            String baseUrl, boolean viewerCanSee) {

        return room.getMembers().stream()
                .filter(this::isActiveMember)
                .filter(m -> room.isGroup() || !m.getUserId().equals(currentUserId))
                .sorted(Comparator
                        .comparing((ConversationMember m) ->
                                        m.getJoinedAt() != null ? m.getJoinedAt() : LocalDateTime.MIN,
                                Comparator.reverseOrder())
                        .thenComparingInt(m -> {
                            MemberRole r = m.getRole() != null ? m.getRole() : MemberRole.MEMBER;
                            return r == MemberRole.OWNER ? 0 : (r == MemberRole.ADMIN ? 1 : 2);
                        }))
                .map(m -> {
                    ChatUser memberInfo = userCache.get(m.getUserId());
                    boolean canSeeStatus = viewerCanSee && memberInfo != null && memberInfo.isShowSeenStatus();

                    return ConversationMemberResponse.builder()
                            .userId(m.getUserId())
                            .fullName(memberInfo != null ? memberInfo.getFullName() : "Người dùng")
                            .avatar(memberInfo != null && memberInfo.getAvatar() != null
                                    ? baseUrl + memberInfo.getAvatar() : null)
                            .lastReadMessageId(canSeeStatus ? m.getLastReadMessageId() : null)
                            .role(m.getRole() != null ? m.getRole() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public OffsetDateTime toOffset(LocalDateTime time) {
        if (time == null) return null;
        return time.atOffset(ZoneOffset.ofHours(7));
    }

    public OffsetDateTime toOffsetFromMongo(Object time) {
        if (time == null) return null;
        if (time instanceof LocalDateTime localDateTime) return toOffset(localDateTime);
        if (time instanceof Date date) return date.toInstant().atOffset(ZoneOffset.ofHours(7));
        return null;
    }

    public boolean isPhoneNumber(String query) {
        return query.matches("\\d{9,11}");
    }

    public String getBaseUrl() {
        return S3Util.getS3BaseUrl(bucketName, region);
    }
}
