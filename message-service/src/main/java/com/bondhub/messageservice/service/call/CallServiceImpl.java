package com.bondhub.messageservice.service.call;

import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.enums.MessageType;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.messageservice.client.FriendServiceClient;
import com.bondhub.messageservice.client.UserServiceClient;
import com.bondhub.messageservice.dto.request.CallRequest;
import com.bondhub.messageservice.dto.response.CallResponse;
import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.messageservice.mapper.CallMapper;
import com.bondhub.messageservice.mapper.MessageMapper;
import com.bondhub.messageservice.model.CallSession;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.repository.CallSessionRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.MessageRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CallServiceImpl implements CallService {

    CallSessionRepository callSessionRepository;
    CallMapper callMapper;
    SecurityUtil securityUtil;
    UserServiceClient userServiceClient;
    FriendServiceClient friendServiceClient;
    StringRedisTemplate stringRedisTemplate;
    KafkaTemplate<String, Object> kafkaTemplate;
    ConversationRepository conversationRepository;
    MessageRepository messageRepository;
    MessageMapper messageMapper;
    MongoTemplate mongoTemplate;
    S3UtilV2 s3UtilV2;

    static final String USER_STATUS_KEY_PREFIX = "user:status:";
    static final String BUSY = "BUSY";
    static final Duration RINGING_TTL = Duration.ofSeconds(60);
    static final Duration IN_CALL_TTL = Duration.ofHours(2);

    @NonFinal
    @Value("${zego.app-id}")
    long zegoAppId;

    @NonFinal
    @Value("${zego.server-secret}")
    String zegoServerSecret;

    @NonFinal
    @Value("${kafka.topics.socket-events:socket-events}")
    String socketEventsTopic;

    @Override
    @CircuitBreaker(name = "userService", fallbackMethod = "initiateCallFallback")
    public CallResponse initiateCall(CallRequest request) {
        String callerId = securityUtil.getCurrentUserId();
        String receiverId = request.receiverId();

        log.info("User {} initiating call to user {}", callerId, receiverId);

        if (callerId.equals(receiverId)) {
            throw new AppException(ErrorCode.CALL_SELF_NOT_ALLOWED);
        }

        if (isUserBusy(callerId)) {
            throw new AppException(ErrorCode.CALL_ALREADY_IN_PROGRESS);
        }

        if (isUserBusy(receiverId)) {
            throw new AppException(ErrorCode.CALL_USER_BUSY);
        }

        UserSummaryResponse callerInfo = userServiceClient.getUserById(callerId).data();
        UserSummaryResponse receiverInfo = userServiceClient.getUserById(receiverId).data();

        if (receiverInfo == null) {
            throw new AppException(ErrorCode.CALL_USER_NOT_FOUND);
        }

        String roomId = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String rtcToken = generateZegoToken(roomId, callerId);

        CallSession session = CallSession.builder()
                .callerId(callerId)
                .callerName(callerInfo != null ? callerInfo.fullName() : "Unknown")
                .callerAvatar(callerInfo != null ? callerInfo.avatar() : null)
                .receiverId(receiverId)
                .receiverName(receiverInfo.fullName())
                .receiverAvatar(receiverInfo.avatar())
                .roomId(roomId)
                .status(CallSession.CallStatus.RINGING)
                .startTime(LocalDateTime.now())
                .build();

        session = callSessionRepository.save(session);
        log.info("Call session created: {} | room: {}", session.getId(), roomId);

        // Mark caller as BUSY with short ringing TTL (auto-expire if no answer)
        setUserBusy(callerId, RINGING_TTL);

        sendCallNotification(session);

        return callMapper.toCallResponse(session, rtcToken, zegoAppId);
    }

    @Override
    public CallResponse acceptCall(String sessionId) {
        String userId = securityUtil.getCurrentUserId();
        CallSession session = getSession(sessionId);

        if (!session.getReceiverId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        session.setStatus(CallSession.CallStatus.IN_PROGRESS);
        session.setStartTime(LocalDateTime.now()); // actual call start time
        callSessionRepository.save(session);

        // Extend both users' BUSY status for in-call duration
        setUserBusy(session.getCallerId(), IN_CALL_TTL);
        setUserBusy(userId, IN_CALL_TTL);

        String rtcToken = generateZegoToken(session.getRoomId(), userId);
        log.info("Call accepted by user {} | session: {}", userId, sessionId);

        // Notify caller to join room
        sendCallSignal(session.getCallerId(), session.getId(), "ACCEPTED", session.getRoomId());

        return callMapper.toCallResponse(session, rtcToken, zegoAppId);
    }

    @Override
    public void rejectCall(String sessionId) {
        String userId = securityUtil.getCurrentUserId();
        CallSession session = getSession(sessionId);

        session.setStatus(CallSession.CallStatus.REJECTED);
        session.setEndTime(LocalDateTime.now());
        callSessionRepository.save(session);

        clearUserBusy(session.getCallerId());
        clearUserBusy(session.getReceiverId());

        log.info("Call rejected by user {} | session: {}", userId, sessionId);

        // Notify caller that call was rejected
        sendCallSignal(session.getCallerId(), session.getId(), "REJECTED", null);

        saveCallMessage(session, "rejected");
    }

    @Override
    public void endCall(String sessionId) {
        String userId = securityUtil.getCurrentUserId();
        CallSession session = getSession(sessionId);

        session.setStatus(CallSession.CallStatus.ENDED);
        session.setEndTime(LocalDateTime.now());
        callSessionRepository.save(session);

        clearUserBusy(session.getCallerId());
        clearUserBusy(session.getReceiverId());

        log.info("Call ended by user {} | session: {}", userId, sessionId);

        // Notify the other user that call ended
        String otherUserId = userId.equals(session.getCallerId()) ? session.getReceiverId() : session.getCallerId();
        sendCallSignal(otherUserId, session.getId(), "ENDED", null);

        saveCallMessage(session, "ended");
    }

    @Override
    public void cancelCall(String sessionId) {
        String userId = securityUtil.getCurrentUserId();
        CallSession session = getSession(sessionId);

        // Caller cancels or ringing timed out → MISSED
        session.setStatus(CallSession.CallStatus.MISSED);
        session.setEndTime(LocalDateTime.now());
        callSessionRepository.save(session);

        clearUserBusy(session.getCallerId());
        clearUserBusy(session.getReceiverId());

        log.info("Call cancelled/missed by user {} | session: {}", userId, sessionId);

        // Notify receiver that caller cancelled
        sendCallSignal(session.getReceiverId(), session.getId(), "CANCELLED", null);

        saveCallMessage(session, "missed");
    }

    @Override
    public CallResponse getCallToken(String sessionId) {
        String userId = securityUtil.getCurrentUserId();
        CallSession session = getSession(sessionId);

        String rtcToken = generateZegoToken(session.getRoomId(), userId);
        return callMapper.toCallResponse(session, rtcToken, zegoAppId);
    }

    // ─── Private helpers ────────────────────────────────────────────

    private CallSession getSession(String sessionId) {
        return callSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.CALL_SESSION_NOT_FOUND));
    }

    private boolean isUserBusy(String userId) {
        String status = stringRedisTemplate.opsForValue().get(USER_STATUS_KEY_PREFIX + userId);
        return BUSY.equals(status);
    }

    private void setUserBusy(String userId, Duration ttl) {
        stringRedisTemplate.opsForValue().set(USER_STATUS_KEY_PREFIX + userId, BUSY, ttl);
    }

    private void clearUserBusy(String userId) {
        stringRedisTemplate.delete(USER_STATUS_KEY_PREFIX + userId);
    }

    private String generateZegoToken(String roomId, String userId) {
        try {
            log.debug("Generating Zego token for room: {}, user: {}", roomId, userId);
            return roomId + ":" + userId + ":" + System.currentTimeMillis();
        } catch (Exception e) {
            log.error("Failed to generate Zego RTC token for room: {}, user: {}", roomId, userId, e);
            throw new AppException(ErrorCode.CALL_TOKEN_GENERATION_FAILED);
        }
    }

    private void sendCallSignal(String targetUserId, String sessionId, String signal, String roomId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("signal", signal);
        if (roomId != null)
            payload.put("roomId", roomId);

        kafkaTemplate.send(socketEventsTopic, new SocketEvent(
                SocketEventType.CALL_SIGNAL, targetUserId,
                "/queue/call-signals", payload));
        log.info("Call signal {} sent to user {} for session {}", signal, targetUserId, sessionId);
    }

    private void sendCallNotification(CallSession session) {
        RawNotificationEvent event = RawNotificationEvent.builder()
                .recipientId(session.getReceiverId())
                .actorId(session.getCallerId())
                .actorName(session.getCallerName())
                .actorAvatar(session.getCallerAvatar() != null ? session.getCallerAvatar() : "")
                .type(NotificationType.CALL)
                .referenceId(session.getId())
                .payload(Map.of(
                        "sessionId", session.getId(),
                        "roomId", session.getRoomId(),
                        "callerName", session.getCallerName(),
                        "callerAvatar", session.getCallerAvatar() != null ? session.getCallerAvatar() : ""))
                .occurredAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send("noti.raw", session.getReceiverId(), event);
        log.info("Call notification published to noti.raw for session: {}", session.getId());
    }

    /**
     * Save a CALL message into the 1-1 conversation so both users see it in chat
     * history.
     */
    private void saveCallMessage(CallSession session, String action) {
        try {
            var conversationOpt = conversationRepository.findDirectConversation(
                    session.getCallerId(), session.getReceiverId());

            if (conversationOpt.isEmpty()) {
                log.warn("No direct conversation found for call {} between {} and {}",
                        session.getId(), session.getCallerId(), session.getReceiverId());
                return;
            }

            Conversation conversation = conversationOpt.get();

            long durationSeconds = 0;
            if ("ended".equals(action) && session.getStartTime() != null && session.getEndTime() != null) {
                durationSeconds = Duration.between(session.getStartTime(), session.getEndTime()).getSeconds();
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("callAction", action);
            metadata.put("sessionId", session.getId());
            metadata.put("durationSeconds", durationSeconds);
            metadata.put("callerId", session.getCallerId());
            metadata.put("callerName", session.getCallerName());
            metadata.put("receiverId", session.getReceiverId());
            metadata.put("receiverName", session.getReceiverName());

            String content;
            switch (action) {
                case "ended" -> content = "Cuộc gọi video - " + formatDuration(durationSeconds);
                case "missed" -> content = "Cuộc gọi nhỡ";
                case "rejected" -> content = "Cuộc gọi bị từ chối";
                default -> content = "Cuộc gọi video";
            }

            Message message = Message.builder()
                    .conversationId(conversation.getId())
                    .senderId(session.getCallerId())
                    .senderName(session.getCallerName())
                    .senderAvatar(session.getCallerAvatar())
                    .content(content)
                    .type(MessageType.CALL)
                    .metadata(metadata)
                    .build();
            message.setCreatedAt(LocalDateTime.now());

            Message savedMessage = messageRepository.save(message);

            // Update conversation's lastMessage
            Query query = new Query(Criteria.where("id").is(conversation.getId()));
            Update update = new Update().set("lastMessage", LastMessageInfo.builder()
                    .messageId(savedMessage.getId())
                    .senderId(session.getCallerId())
                    .content(content)
                    .timestamp(savedMessage.getCreatedAt())
                    .type(MessageType.CALL)
                    .metadata(metadata)
                    .build());

            Conversation updatedRoom = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true), Conversation.class);

            // Broadcast call message to both users via socket
            if (updatedRoom != null) {
                String baseUrl = s3UtilV2.getS3BaseUrl();
                updatedRoom.getMembers().forEach(member -> {
                    Integer unread = updatedRoom.getUnreadCounts() != null
                            ? updatedRoom.getUnreadCounts().getOrDefault(member.getUserId(), 0)
                            : 0;
                    boolean isFromMe = member.getUserId().equals(session.getCallerId());

                    ChatNotification notification = messageMapper.mapToChatNotification(savedMessage, baseUrl, unread);
                    notification = notification.toBuilder().isFromMe(isFromMe).build();

                    kafkaTemplate.send(socketEventsTopic, new SocketEvent(
                            SocketEventType.MESSAGE, member.getUserId(),
                            "/queue/messages", notification));
                });
            }

            log.info("Call message saved for session {} in conversation {}", session.getId(), conversation.getId());
        } catch (Exception e) {
            log.error("Failed to save call message for session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " giây";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (remainingSeconds == 0) {
            return minutes + " phút";
        }
        return minutes + " phút " + remainingSeconds + " giây";
    }

    @SuppressWarnings("unused")
    private CallResponse initiateCallFallback(CallRequest request, Throwable t) {
        if (t instanceof AppException) {
            throw (AppException) t;
        }
        log.error("Circuit breaker triggered for initiateCall: {}", t.getMessage());
        throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
    }
}
