package com.bondhub.messageservice.config;

import com.bondhub.common.enums.Status;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ChatUserRepository chatUserRepository;

    @Override
    public void run(String... args) {
        initializeAiAssistant();
    }

    private void initializeAiAssistant() {
        String aiId = "ai-assistant-001";
        if (!chatUserRepository.existsById(aiId)) {
            log.info("Initializing AI Assistant user...");
            ChatUser aiUser = ChatUser.builder()
                    .id(aiId)
                    .fullName("Bondhub AI")
                    .avatar("bondhub-ai.png")
                    .status(Status.ONLINE)
                    .lastUpdatedAt(LocalDateTime.now())
                    .showSeenStatus(false)
                    .build();
            chatUserRepository.save(aiUser);
            log.info("AI Assistant user initialized successfully.");
        } else {
            chatUserRepository.findById(aiId).ifPresent(ai -> {
                ai.setFullName("Bondhub AI");
                ai.setAvatar("bondhub-ai.png");
                ai.setShowSeenStatus(false);
                chatUserRepository.save(ai);
            });
        }
    }
}
