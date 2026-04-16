package com.bondhub.aiservice.config;

import dev.langchain4j.data.message.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.bson.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new ChatMessageWritingConverter());
        converters.add(new ChatMessageReadingConverter());
        return new MongoCustomConversions(converters);
    }

    @WritingConverter
    public static class ChatMessageWritingConverter implements Converter<ChatMessage, Document> {
        @Override
        public Document convert(ChatMessage source) {
            Document doc = new Document();
            doc.put("type", source.type().name());
            doc.put("text", source.text());
            
            if (source instanceof AiMessage ai) {
                if (ai.toolExecutionRequests() != null) {
                    // Handle tool calls if needed in the future
                }
            }
            return doc;
        }
    }

    @ReadingConverter
    public static class ChatMessageReadingConverter implements Converter<Document, ChatMessage> {
        @Override
        public ChatMessage convert(Document source) {
            String type = source.getString("type");
            String text = source.getString("text");

            // Guard: skip old/malformed documents without a 'type' field
            if (type == null || text == null) {
                return null;
            }

            return switch (ChatMessageType.valueOf(type)) {
                case USER -> UserMessage.from(text);
                case AI -> AiMessage.from(text);
                case SYSTEM -> SystemMessage.from(text);
                case TOOL_EXECUTION_RESULT -> null;
            };
        }
    }
}
