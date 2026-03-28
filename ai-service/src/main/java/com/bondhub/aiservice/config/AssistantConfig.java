package com.bondhub.aiservice.config;

import com.bondhub.aiservice.memory.MongoChatMemoryStore;
import com.bondhub.aiservice.service.*;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistantConfig {

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Value("${openai.logic-model-name:gpt-4o-mini}")
    private String logicModelName;

    @Value("${openai.logic-temperature:0.0}")
    private double logicTemperature;

    @Value("${openai.generator-model-name:gpt-4o}")
    private String generatorModelName;

    @Value("${openai.generator-temperature:0.7}")
    private double generatorTemperature;

    // ======================================================
    // MODEL BEANS
    // ======================================================

    /**
     * Logic model (gpt-4o-mini): Analyzer (Slot Filling) & Grader (RAG grading).
     * Temperature = 0.0 — logic xác định, tuân thủ định dạng MISSING: cực tốt.
     */
    @Bean
    @Qualifier("logicModel")
    ChatLanguageModel logicChatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(logicModelName)
                .temperature(logicTemperature)
                .maxRetries(3)
                .build();
    }

    /**
     * Generator model (gpt-4o): Tổng hợp câu trả lời cuối.
     * Temperature = 0.7 — văn phong tiếng Việt mượt mà, hiểu ngữ cảnh sâu.
     */
    @Bean
    @Qualifier("generatorModel")
    ChatLanguageModel generatorChatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(generatorModelName)
                .temperature(generatorTemperature)
                .maxRetries(3)
                .build();
    }

    /**
     * Streaming model (gpt-4o): Chat realtime với người dùng qua SSE.
     */
    @Bean
    StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(generatorModelName) // dùng gpt-4o cho streaming
                .build();
    }

    // ======================================================
    // MEMORY
    // ======================================================

    @Bean
    ChatMemoryProvider chatMemoryProvider(MongoChatMemoryStore chatMemoryStore) {
        return chatId -> MessageWindowChatMemory.builder()
                .id(chatId)
                .maxMessages(10)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    // ======================================================
    // AI SERVICE BEANS
    // ======================================================

    @Bean
    BondHubAssistant assistant(StreamingChatLanguageModel streamingModel,
                               ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(BondHubAssistant.class)
                .streamingChatLanguageModel(streamingModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    /** SmartReply: gpt-4o-mini — nhanh, rẻ, đủ chính xác */
    @Bean
    SmartReplyService smartReplyService(@Qualifier("logicModel") ChatLanguageModel chatModel) {
        return AiServices.builder(SmartReplyService.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    /** Summarization: gpt-4o-mini — tóm tắt chuẩn, không bay bổng */
    @Bean
    SummarizationService summarizationService(@Qualifier("logicModel") ChatLanguageModel chatModel) {
        return AiServices.builder(SummarizationService.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    /** Analyzer: gpt-4o-mini — phát hiện MISSING context, xác định */
    @Bean
    AnalyzerService analyzerService(@Qualifier("logicModel") ChatLanguageModel chatModel) {
        return AiServices.builder(AnalyzerService.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    /** Grader: gpt-4o-mini — chấm điểm RAG CORRECT/AMBIGUOUS/INCORRECT */
    @Bean
    GraderService graderService(@Qualifier("logicModel") ChatLanguageModel chatModel) {
        return AiServices.builder(GraderService.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    /** Generator: gpt-4o — tổng hợp câu trả lời cuối, văn phong tốt */
    @Bean
    GeneratorService generatorService(@Qualifier("generatorModel") ChatLanguageModel chatModel) {
        return AiServices.builder(GeneratorService.class)
                .chatLanguageModel(chatModel)
                .build();
    }
}
