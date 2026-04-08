package com.bondhub.aiservice.service.core.crag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CragGraphConfig {

    public static final String NODE_CLARIFY = "clarify";
    public static final String NODE_RETRIEVE = "retrieve";
    public static final String NODE_GENERATE = "generate";
    public static final String NODE_WEB_SEARCH = "web_search";
    public static final String NODE_MARK_LOW_CONFIDENCE = "mark_low_confidence";

    @Value("${ai.crag.max-web-retries:1}")
    private int maxWebRetries;

    public String nextAfterAnalyze(String route) {
        String normalized = route == null ? "" : route.trim().toUpperCase();
        if (normalized.startsWith("MISSING:")) {
            return NODE_CLARIFY;
        }
        if ("COMPLETE".equals(normalized)) {
            return NODE_RETRIEVE;
        }
        return NODE_GENERATE;
    }

    public String nextAfterGrade(String grade, int retryCount) {
        String normalized = grade == null ? "INCORRECT" : grade.trim().toUpperCase();
        if ("CORRECT".equals(normalized)) {
            return NODE_GENERATE;
        }
        int retryLimit = Math.max(0, maxWebRetries);
        if (retryCount >= retryLimit) {
            return NODE_MARK_LOW_CONFIDENCE;
        }
        return NODE_WEB_SEARCH;
    }
}
