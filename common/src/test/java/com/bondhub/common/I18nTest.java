package com.bondhub.common;

import com.bondhub.common.config.I18nConfig;
import com.bondhub.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = { I18nConfig.class, I18nTest.TestConfig.class })
@org.springframework.test.context.TestPropertySource(properties = "spring.messages.basename=i18n/messages")
public class I18nTest {

    @org.springframework.context.annotation.Configuration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public static org.springframework.context.support.PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
        }
    }

    @Autowired
    private MessageSource messageSource;

    @Test
    public void testEnglishMessages() {
        String message = messageSource.getMessage(ErrorCode.AUTH_UNAUTHENTICATED.getMessageKey(), null, Locale.ENGLISH);
        assertEquals("Authentication failed or not provided", message);
    }

    @Test
    public void testVietnameseMessages() {
        String message = messageSource.getMessage(ErrorCode.AUTH_UNAUTHENTICATED.getMessageKey(), null,
                new Locale("vi"));
        assertEquals("Xác thực thất bại hoặc chưa được cung cấp", message);
    }

    @Test
    public void testDefaultMessage() {
        // Test with a non-existent locale (should fallback to default Vietnamese)
        String message = messageSource.getMessage(ErrorCode.AUTH_UNAUTHENTICATED.getMessageKey(), null, Locale.JAPAN);
        assertEquals("Xác thực thất bại hoặc chưa được cung cấp", message);
    }
}
