package com.bondhub.common.config.kafka;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kafka.topics")
@Getter
@Setter
public class KafkaTopicProperties {

    private AccountEvents accountEvents = new AccountEvents();
    private UserEvents userEvents = new UserEvents();
    private MessageEvents messageEvents = new MessageEvents();
    private NotificationEvents notificationEvents = new NotificationEvents();

    @Getter
    @Setter
    public static class AccountEvents {
        private String registered = "account.registered";
        private String updated = "account.updated";
        private String deleted = "account.deleted";
        private String verified = "account.verified";
        private String enabled = "account.enabled";
        private String disabled = "account.disabled";
    }

    @Getter
    @Setter
    public static class UserEvents {
        private String created = "user.created";
        private String updated = "user.updated";
        private String deleted = "user.deleted";
        private String index = "user.index";
    }

    @Getter
    @Setter
    public static class MessageEvents {

    }

    @Getter
    @Setter
    public static class NotificationEvents {

    }

    @Getter
    @Setter
    public static class SystemEvents {

    }


}
