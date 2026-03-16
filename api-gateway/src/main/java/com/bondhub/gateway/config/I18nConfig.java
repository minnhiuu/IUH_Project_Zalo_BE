package com.bondhub.gateway.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * I18n Configuration for API Gateway (Reactive)
 * Only configures MessageSource - no LocaleResolver needed for reactive apps
 */
@Configuration
public class I18nConfig {

    /**
     * Message source configuration
     * Loads messages from i18n/messages properties files
     */
    @Bean
    public MessageSource messageSource(
            @org.springframework.beans.factory.annotation.Value("${spring.messages.basename:i18n/messages}") String basename) {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        String[] basenames = basename.split(",");
        for (int i = 0; i < basenames.length; i++) {
            basenames[i] = basenames[i].trim().replace('/', '.');
        }
        messageSource.setBasenames(basenames);
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }
}
