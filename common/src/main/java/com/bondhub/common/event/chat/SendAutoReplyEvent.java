package com.bondhub.common.event.chat;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendAutoReplyEvent {

    private String conversationId;

    private String quietUserId;

    private String receiverId;

    private String messageKey;
}
