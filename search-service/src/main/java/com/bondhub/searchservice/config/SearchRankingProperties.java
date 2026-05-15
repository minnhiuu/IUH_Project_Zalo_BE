package com.bondhub.searchservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "search.ranking")
@Data
public class SearchRankingProperties {

    private User user = new User();

    @Data
    public static class User {
        private double exactPhoneBoost = 10_000.0;
        private double esScoreMultiplier = 100.0;
        private double acceptedFriendBoost = 1_000.0;
        private double pendingReceivedBoost = 800.0;
        private double pendingSentBoost = 500.0;
        private double mutualFriendWeight = 30.0;
        private int mutualFriendCap = 10;
        private double sharedGroupWeight = 15.0;
        private int sharedGroupCap = 10;
        private double contactScoreWeight = 50.0;
        private double contactScoreCap = 5.0;
        private double recentInteractionWeight = 40.0;
        private double recentInteractionCap = 5.0;
    }
}
