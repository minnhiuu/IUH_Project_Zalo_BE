package com.bondhub.messageservice.service.message;

import com.bondhub.common.dto.client.messageservice.AttachmentRequest;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.enums.MessageType;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.publisher.RawNotificationEventPublisher;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.messageservice.mapper.MessageMapper;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.GroupSettings;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.publisher.ChatInteractionEventPublisher;
import com.bondhub.messageservice.publisher.MessageIndexEventPublisher;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.MessageRepository;
import com.bondhub.messageservice.service.conversation.ConversationHelper;
import com.bondhub.messageservice.service.conversation.ConversationService;
import com.bondhub.messageservice.dto.response.ChatNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ChatUserRepository chatUserRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private SecurityUtil securityUtil;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ConversationService conversationService;
    @Mock
    private ConversationHelper conversationHelper;
    @Mock
    private MessageIndexEventPublisher messageIndexEventPublisher;
    @Mock
    private ChatInteractionEventPublisher chatInteractionEventPublisher;
    @Mock
    private RawNotificationEventPublisher rawNotificationEventPublisher;
    @Mock
    private S3UtilV2 s3UtilV2;

    @InjectMocks
    private MessageServiceImpl messageService;

    private String currentUserId;
    private Conversation conversation;
    private ConversationMember currentMember;

    @BeforeEach
    void setUp() {
        currentUserId = "user1";
        
        currentMember = ConversationMember.builder()
                .userId(currentUserId)
                .active(true)
                .build();
                
        conversation = Conversation.builder()
                .id("conv1")
                .isGroup(true)
                .members(Set.of(currentMember))
                .settings(GroupSettings.builder().memberCanSendMessages(true).build())
                .build();

        org.mockito.Mockito.lenient().when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);
        org.mockito.Mockito.lenient().when(s3UtilV2.getS3BaseUrl()).thenReturn("http://s3.local");
    }

    @Test
    @DisplayName("UTCID01 - Send message success to an existing conversation")
    void sendMessage_Success() {
        MessageSendRequest request = new MessageSendRequest("conv1", null, "Hello World", null, null, false, null);
        
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation));
        
        // leniently mock active member
        org.mockito.Mockito.lenient().when(conversationHelper.isActiveMember(any(ConversationMember.class))).thenReturn(true);
        
        ChatUser sender = new ChatUser();
        sender.setId(currentUserId);
        sender.setFullName("User 1");
        when(chatUserRepository.findById(currentUserId)).thenReturn(Optional.of(sender));
        
        when(messageRepository.save(any(Message.class))).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId("msg1");
            m.setCreatedAt(LocalDateTime.now());
            m.setStatus(MessageStatus.NORMAL);
            return m;
        });

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Conversation.class)))
                .thenReturn(conversation);
                
        ChatNotification mockNotif = ChatNotification.builder().build();
        when(messageMapper.mapToChatNotification(any(Message.class), any(), eq(0))).thenReturn(mockNotif);

        assertDoesNotThrow(() -> messageService.sendMessage("conv1", request));

        verify(messageRepository, times(1)).save(any(Message.class));
        verify(kafkaTemplate, times(1)).send(eq("message-created"), any());
        verify(messageIndexEventPublisher, times(1)).publishIndexRequest(any(Message.class));
    }

    @Test
    @DisplayName("UTCID02 - Send message via lazy creation (direct chat)")
    void sendMessage_LazyCreation_Success() {
        MessageSendRequest request = new MessageSendRequest(null, "user2", "Hello User 2", null, null, false, null);
        
        Conversation directConv = Conversation.builder()
                .id("direct1")
                .isGroup(false)
                .members(Set.of(
                        ConversationMember.builder().userId(currentUserId).active(true).build(),
                        ConversationMember.builder().userId("user2").active(true).build()
                ))
                .build();
                
        when(conversationService.getOrCreateDirectConversation(currentUserId, "user2")).thenReturn(directConv);
        org.mockito.Mockito.lenient().when(conversationHelper.isActiveMember(any(ConversationMember.class))).thenReturn(true);
        when(chatUserRepository.findById(currentUserId)).thenReturn(Optional.empty());

        when(messageRepository.save(any(Message.class))).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId("msg2");
            m.setCreatedAt(LocalDateTime.now());
            m.setStatus(MessageStatus.NORMAL);
            return m;
        });
        
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Conversation.class)))
                .thenReturn(directConv);
                
        ChatNotification mockNotif = ChatNotification.builder().build();
        when(messageMapper.mapToChatNotification(any(Message.class), any(), eq(0))).thenReturn(mockNotif);

        assertDoesNotThrow(() -> messageService.sendMessage(null, request));
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    @DisplayName("UTCID03 - Send message with attachments")
    void sendMessage_WithAttachments_Success() {
        // Assume attachment format is mapped correctly in request
        MessageSendRequest request = new MessageSendRequest("conv1", null, "Here is a file", null, null, false, List.of(
            new AttachmentRequest("img_key", "/path/to/image.jpg", "image.jpg", "image.jpg", "image/jpeg", 1024L)
        ));
        
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation));
        org.mockito.Mockito.lenient().when(conversationHelper.isActiveMember(any(ConversationMember.class))).thenReturn(true);
        when(chatUserRepository.findById(currentUserId)).thenReturn(Optional.empty());

        when(messageRepository.save(any(Message.class))).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId("msg3");
            m.setCreatedAt(LocalDateTime.now());
            m.setStatus(MessageStatus.NORMAL);
            return m;
        });
        
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Conversation.class)))
                .thenReturn(conversation);
                
        ChatNotification mockNotif = ChatNotification.builder().build();
        when(messageMapper.mapToChatNotification(any(Message.class), any(), eq(0))).thenReturn(mockNotif);

        assertDoesNotThrow(() -> messageService.sendMessage("conv1", request));
        verify(messageRepository, times(1)).save(argThat(msg -> msg.getType() == MessageType.IMAGE));
    }

    @Test
    @DisplayName("UTCID04 - Send message to non-existent conversation")
    void sendMessage_ConversationNotFound() {
        MessageSendRequest request = new MessageSendRequest("conv_not_found", null, "Hello", null, null, false, null);
        
        when(conversationRepository.findById("conv_not_found")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> messageService.sendMessage("conv_not_found", request));
        assertEquals(ErrorCode.CHAT_ROOM_NOT_FOUND, ex.getErrorCode());
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID05 - Send message when user is not active member")
    void sendMessage_NotActiveMember() {
        MessageSendRequest request = new MessageSendRequest("conv1", null, "Hello", null, null, false, null);
        
        ConversationMember inactiveMember = ConversationMember.builder().userId(currentUserId).active(false).build();
        conversation.setMembers(Set.of(inactiveMember));
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation));

        AppException ex = assertThrows(AppException.class, () -> messageService.sendMessage("conv1", request));
        assertEquals(ErrorCode.CHAT_MEMBER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("UTCID06 - Send message when group setting disabled")
    void sendMessage_SettingDisabled() {
        MessageSendRequest request = new MessageSendRequest("conv1", null, "Hello", null, null, false, null);

        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation));

        doThrow(new AppException(ErrorCode.CHAT_SETTING_RESTRICTED))
                .when(conversationHelper).assertSettingAllowed(any(), any(), any());
        AppException ex = assertThrows(AppException.class, () -> messageService.sendMessage("conv1", request));
        assertEquals(ErrorCode.CHAT_SETTING_RESTRICTED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UTCID07 - Toggle reaction on a message successfully")
    void toggleReaction_Success() {
        Message message = new Message();
        message.setId("msg1");
        message.setConversationId("conv1");
        Map<String, List<String>> reactions = new HashMap<>(); // Empty initially
        message.setReactions(reactions);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation));

        assertDoesNotThrow(() -> messageService.toggleReaction("msg1", ":smile:"));

        verify(messageRepository, times(1)).save(message);

        assertNotNull(message.getReactions());
        assertTrue(message.getReactions().containsKey(":smile:"));
        assertTrue(message.getReactions().get(":smile:").contains(currentUserId));

        verify(kafkaTemplate, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("UTCID08 - Toggle reaction on non-existent message")
    void toggleReaction_MessageNotFound() {
        when(messageRepository.findById("msg99")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> messageService.toggleReaction("msg99", ":smile:"));
        assertEquals(ErrorCode.MESSAGE_NOT_FOUND, ex.getErrorCode());

        verify(messageRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    @DisplayName("UTCID09 - Remove all my reactions successfully")
    void removeAllMyReactions_Success() {
        Message message = new Message();
        message.setId("msg1");
        message.setConversationId("conv1");

        Map<String, List<String>> reactions = new HashMap<>();
        List<String> smileUsers = new ArrayList<>();
        smileUsers.add(currentUserId);
        smileUsers.add("otherUser");
        reactions.put(":smile:", smileUsers);

        List<String> sadUsers = new ArrayList<>();
        sadUsers.add(currentUserId);
        reactions.put(":sad:", sadUsers);

        message.setReactions(reactions);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation));

        assertDoesNotThrow(() -> messageService.removeAllMyReactions("msg1"));

        verify(messageRepository, times(1)).save(message);

        if (message.getReactions() != null) {
            if (message.getReactions().containsKey(":smile:")) {
                assertFalse(message.getReactions().get(":smile:").contains(currentUserId));
                assertTrue(message.getReactions().get(":smile:").contains("otherUser"));
            }
            assertFalse(message.getReactions().containsKey(":sad:")); // Should be empty and removed entirely
        }

        verify(kafkaTemplate, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("UTCID10 - Remove all my reactions on a message with no reactions")
    void removeAllMyReactions_NoReactions_Success() {
        Message message = new Message();
        message.setId("msg1");
        message.setConversationId("conv1");
        message.setReactions(null); // No reactions

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation));

        assertDoesNotThrow(() -> messageService.removeAllMyReactions("msg1"));

        verify(messageRepository, never()).save(message);
        verify(kafkaTemplate, never()).send(any(), any());
    }
}
