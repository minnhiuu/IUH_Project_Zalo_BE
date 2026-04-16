package com.bondhub.notificationservices.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FcmConfig {

    @Value("${app.firebase.config-path:}")
    private String firebaseConfigPath;

    @PostConstruct
    public void initialize() {
        try {

            if (!FirebaseApp.getApps().isEmpty()) {
                log.info("Firebase already initialized");
                return;
            }

            if (firebaseConfigPath == null || firebaseConfigPath.isBlank()) {
                throw new IllegalStateException("Firebase config path is not set");
            }

            log.info("Initializing Firebase from path: {}", firebaseConfigPath);

            try (InputStream serviceAccount =
                         new FileInputStream(firebaseConfigPath)) {

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
            }

            log.info("Firebase initialized successfully");

        } catch (Exception e) {
            log.error("Firebase initialization failed", e);
            throw new RuntimeException("Failed to initialize Firebase", e);
        }
    }
}

