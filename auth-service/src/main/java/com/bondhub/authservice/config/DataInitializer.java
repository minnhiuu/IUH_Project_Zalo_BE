package com.bondhub.authservice.config;

import com.bondhub.authservice.model.Account;
import com.bondhub.authservice.repository.AccountRepository;
import com.bondhub.common.enums.Role;
import com.bondhub.common.event.account.AccountRegisteredEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventPublisher outboxEventPublisher;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            if (!accountRepository.existsByEmail(adminEmail)) {
                log.info("Creating default admin account...");
                Account admin = Account.builder()
                        .email(adminEmail)
                        .password(passwordEncoder.encode(adminPassword))
                        .phoneNumber("0900000000")
                        .role(Role.ADMIN)
                        .isVerified(true)
                        .enabled(true)
                        .build();
                
                admin = accountRepository.save(admin);
                log.info("Admin account created successfully: {}", adminEmail);

                AccountRegisteredEvent event = AccountRegisteredEvent.builder()
                        .accountId(admin.getId())
                        .email(admin.getEmail())
                        .fullName("System Administrator")
                        .phoneNumber(admin.getPhoneNumber())
                        .timestamp(System.currentTimeMillis())
                        .build();

                outboxEventPublisher.saveAndPublish(
                        admin.getId(),
                        "Account",
                        EventType.ACCOUNT_REGISTERED,
                        event
                );
                log.info("User creation event published for admin: {}", adminEmail);
            } else {
                log.info("Admin account already exists.");
            }
        };
    }
}
