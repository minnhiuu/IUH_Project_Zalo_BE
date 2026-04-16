package com.bondhub.notificationservices.utils;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateEngine {
    public String render(String template, Map<String, Object> payload) {
        if (template == null) return "";
        if (payload == null) payload = Collections.emptyMap();
        String result = template;

        Pattern pattern = Pattern.compile("\\{\\{#(.+?)}}(.*?)\\{\\{/\\1}}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String content = matcher.group(2);
            Object value = payload.get(key);

            boolean shouldShow = false;
            if (value instanceof Number) {
                shouldShow = ((Number) value).longValue() > 0;
            } else if (value instanceof Boolean) {
                shouldShow = (Boolean) value;
            } else {
                shouldShow = value != null;
            }

            matcher.appendReplacement(sb, shouldShow ? Matcher.quoteReplacement(content) : "");
        }
        matcher.appendTail(sb);
        result = sb.toString();

        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            result = result.replace(
                    "{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue().toString() : ""
            );
        }

        return result;
    }
}
